package com.trickshotmlg.devkit.scheduler;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Result of an asynchronous computation, with cancellation and a nonblocking synchronous handoff. */
public final class AsyncTaskResult<T> {
    private final TaskScope scope;
    private final TaskHandle computation;
    private final CompletableFuture<T> result = new CompletableFuture<>();
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private final AtomicReference<TaskHandle> continuation = new AtomicReference<>();

    private AsyncTaskResult(TaskScope scope, Callable<T> action) {
        this.scope = Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(action, "action");
        TaskHandle[] handle = new TaskHandle[1];
        handle[0] = scope.async().run(context -> {
            try {
                T value = action.call();
                if (context.task().state() != TaskState.CANCELLED && !cancellationRequested.get()) result.complete(value);
            } catch (Throwable error) {
                result.completeExceptionally(error);
                if (error instanceof Exception exception) throw exception;
                throw (Error) error;
            }
        });
        computation = handle[0];
        computation.completion().whenComplete((ignored, error) -> {
            if (error != null) result.completeExceptionally(unwrap(error));
        });
    }

    static <T> AsyncTaskResult<T> submit(TaskScope scope, Callable<T> action) {
        return new AsyncTaskResult<>(scope, action);
    }

    /** The computation handle. Its terminal state does not include a subsequently registered continuation. */
    public TaskHandle task() { return computation; }

    /** Completion of the background computation; it never blocks the calling thread. */
    public CompletionStage<T> result() { return result.minimalCompletionStage(); }

    /**
     * Schedules {@code action} in the scheduler's synchronous context after successful computation.
     * The continuation is cancelled if this result is cancelled or its owning scope closes first.
     */
    public AsyncTaskResult<T> thenSync(Consumer<? super T> action) {
        Objects.requireNonNull(action, "action");
        result.whenComplete((value, error) -> {
            if (error != null || cancellationRequested.get()) return;
            try {
                TaskHandle scheduled = scope.sync().run(context -> {
                    if (!cancellationRequested.get()) action.accept(value);
                });
                continuation.set(scheduled);
                if (cancellationRequested.get()) scheduled.cancel();
            } catch (IllegalStateException closedScope) {
                // A closed scope rejects the continuation; it must never be detached from its owner.
            }
        });
        return this;
    }

    /** Registers a nonblocking failure observer, invoked by the completion stage's completing/registration thread. */
    public AsyncTaskResult<T> onFailure(Consumer<? super Throwable> action) {
        Objects.requireNonNull(action, "action");
        result.whenComplete((value, error) -> {
            if (error == null) return;
            try { action.accept(unwrap(error)); }
            catch (Throwable observerFailure) { reportObserverFailure(observerFailure); }
        });
        return this;
    }

    /** Cancels the computation if still pending/running and any queued synchronous continuation. */
    public boolean cancel() {
        if (!cancellationRequested.compareAndSet(false, true)) return false;
        boolean changed = computation.cancel();
        TaskHandle scheduled = continuation.get();
        if (scheduled != null) changed |= scheduled.cancel();
        result.completeExceptionally(new CancellationException("Asynchronous task was cancelled"));
        return changed;
    }

    public boolean isCancelled() { return cancellationRequested.get() || computation.state() == TaskState.CANCELLED; }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static void reportObserverFailure(Throwable error) {
        Thread thread = Thread.currentThread();
        Thread.UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
        if (handler != null) {
            try { handler.uncaughtException(thread, error); } catch (Throwable ignored) { }
        }
    }
}
