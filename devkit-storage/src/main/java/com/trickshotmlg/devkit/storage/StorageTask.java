package com.trickshotmlg.devkit.storage;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Cancellable logical handle for asynchronous storage work. Running JDBC calls may continue after cancellation. */
public final class StorageTask<T> {
    public enum State { QUEUED, RUNNING, COMPLETED, CANCELLED, FAILED }
    private final CompletableFuture<T> result = new CompletableFuture<>();
    private final AtomicReference<State> state = new AtomicReference<>(State.QUEUED);
    private volatile Runnable cancellation = () -> { };

    StorageTask() { }
    void cancellation(Runnable cancellation) { this.cancellation = cancellation; }
    boolean begin() { return state.compareAndSet(State.QUEUED, State.RUNNING); }
    boolean complete(T value) {
        if (!state.compareAndSet(State.RUNNING, State.COMPLETED)) return false;
        cancellation = () -> { };
        result.complete(value);
        return true;
    }
    boolean fail(Throwable failure) {
        State current = state.get();
        while (current == State.QUEUED || current == State.RUNNING) {
            if (state.compareAndSet(current, State.FAILED)) {
                cancellation = () -> { };
                result.completeExceptionally(failure);
                return true;
            }
            current = state.get();
        }
        return false;
    }
    boolean cancelState() {
        State current = state.get();
        while (current == State.QUEUED || current == State.RUNNING) {
            if (state.compareAndSet(current, State.CANCELLED)) {
                cancellation = () -> { };
                result.cancel(false);
                return true;
            }
            current = state.get();
        }
        return current == State.CANCELLED;
    }

    /** Read-only result stage; cancelling its derived future does not mutate this task. */
    public CompletionStage<T> result() { return result.minimalCompletionStage(); }
    public State state() { return state.get(); }
    public boolean isDone() {
        State value = state.get();
        return value == State.COMPLETED || value == State.CANCELLED || value == State.FAILED;
    }
    public boolean cancel() {
        Runnable cancelOperation = cancellation;
        boolean cancelled = cancelState();
        if (cancelled) cancelOperation.run();
        return cancelled;
    }
}
