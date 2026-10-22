package com.trickshotmlg.devkit.scheduler;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Internal synchronized task lifecycle shared by all backends. */
final class TaskRecord implements TaskHandle {
    private final long id;
    private final TaskExecutionTarget target;
    private final TaskSchedule schedule;
    private final TaskScope scope;
    private final TaskCallback callback;
    private final TaskFailureHandler failureHandler;
    private final TaskFailurePolicy failurePolicy;
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private TaskState state = TaskState.PENDING;
    private long executionCount;
    private Throwable failure;
    private BackendTask backendTask;
    private Runnable terminalCallback;

    TaskRecord(long id, TaskExecutionTarget target, TaskSchedule schedule, TaskScope scope,
               TaskCallback callback, TaskFailureHandler failureHandler, TaskFailurePolicy failurePolicy) {
        this.id = id;
        this.target = Objects.requireNonNull(target);
        this.schedule = Objects.requireNonNull(schedule);
        this.scope = Objects.requireNonNull(scope);
        this.callback = Objects.requireNonNull(callback);
        this.failureHandler = Objects.requireNonNull(failureHandler);
        this.failurePolicy = Objects.requireNonNull(failurePolicy);
    }

    void setTerminalCallback(Runnable action) {
        boolean alreadyTerminal;
        synchronized (this) {
            terminalCallback = Objects.requireNonNull(action);
            alreadyTerminal = state.terminal();
        }
        if (alreadyTerminal) action.run();
    }

    void attach(BackendTask task) {
        Objects.requireNonNull(task, "task");
        boolean cancel;
        synchronized (this) {
            backendTask = task;
            cancel = state.terminal();
        }
        if (cancel) safeCancel(task);
    }

    void dispatch() {
        synchronized (this) {
            if (state.terminal()) return;
            if (state == TaskState.RUNNING) return; // never overlap one repeating callback
            state = TaskState.RUNNING;
            executionCount++;
        }
        try {
            callback.run(new Context(this));
            if (schedule instanceof TaskSchedule.Once) finishSuccessfully();
            else {
                synchronized (this) {
                    if (state == TaskState.RUNNING) state = TaskState.ACTIVE;
                }
            }
        } catch (Throwable error) {
            if (schedule instanceof TaskSchedule.Repeating
                    && failurePolicy == TaskFailurePolicy.CONTINUE_ON_FAILURE) {
                continueAfterFailure(error);
            } else fail(error);
        }
    }

    private void continueAfterFailure(Throwable error) {
        long count;
        synchronized (this) {
            if (state != TaskState.RUNNING) return;
            failure = error;
            state = TaskState.ACTIVE;
            count = executionCount;
        }
        reportFailure(count, error);
    }

    private void finishSuccessfully() {
        Runnable terminal;
        synchronized (this) {
            if (state != TaskState.RUNNING) return;
            state = TaskState.COMPLETED;
            terminal = terminalCallback;
        }
        completion.complete(null);
        notifyTerminal(terminal);
    }

    void failBeforeDispatch(Throwable error) { fail(error); }

    private void fail(Throwable error) {
        Objects.requireNonNull(error, "error");
        Runnable terminal;
        BackendTask nativeTask;
        long count;
        synchronized (this) {
            if (state.terminal()) return;
            failure = error;
            state = TaskState.FAILED;
            terminal = terminalCallback;
            nativeTask = backendTask;
            count = Math.max(1, executionCount);
        }
        if (nativeTask != null) safeCancel(nativeTask);
        completion.completeExceptionally(error);
        notifyTerminal(terminal);
        reportFailure(count, error);
    }

    private void reportFailure(long count, Throwable error) {
        try {
            failureHandler.onFailure(new TaskFailure(this, target, count, error));
        } catch (Throwable handlerFailure) {
            if (handlerFailure != error) error.addSuppressed(handlerFailure);
            Thread thread = Thread.currentThread();
            Thread.UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
            if (handler != null) {
                try { handler.uncaughtException(thread, error); } catch (Throwable ignored) { }
            }
        }
    }

    @Override public boolean cancel() {
        BackendTask nativeTask;
        Runnable terminal;
        synchronized (this) {
            if (state.terminal()) return false;
            state = TaskState.CANCELLED;
            nativeTask = backendTask;
            terminal = terminalCallback;
        }
        if (nativeTask != null) safeCancel(nativeTask);
        completion.completeExceptionally(new CancellationException("Task " + id + " was cancelled"));
        notifyTerminal(terminal);
        return true;
    }

    private static void safeCancel(BackendTask task) {
        try { task.cancel(); } catch (Throwable ignored) { /* wrapper cancellation remains authoritative */ }
    }

    private static void notifyTerminal(Runnable callback) {
        if (callback != null) callback.run();
    }

    @Override public long id() { return id; }
    @Override public synchronized TaskState state() { return state; }
    synchronized long executionCount() { return executionCount; }
    @Override public synchronized Throwable failure() { return failure; }
    @Override public CompletionStage<Void> completion() { return completion.minimalCompletionStage(); }

    private static final class Context implements TaskContext {
        private final TaskRecord task;
        private Context(TaskRecord task) { this.task = task; }
        @Override public TaskHandle task() { return task; }
        @Override public TaskExecutionTarget target() { return task.target; }
        @Override public TaskScope scope() { return task.scope; }
        @Override public long executionCount() { return task.executionCount(); }
        @Override public void cancel() { task.cancel(); }
    }
}
