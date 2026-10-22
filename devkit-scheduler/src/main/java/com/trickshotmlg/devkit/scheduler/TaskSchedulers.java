package com.trickshotmlg.devkit.scheduler;

import java.util.Objects;

/** Factories for managed schedulers backed by a platform scheduling implementation. */
public final class TaskSchedulers {
    private TaskSchedulers() { }

    /** Creates a scheduler that reports callback failures to the current thread's uncaught exception handler. */
    public static TaskScheduler create(TaskBackend backend) {
        return create(backend, TaskSchedulers::reportUncaught);
    }

    /** Creates a scheduler that owns tasks submitted to {@code backend}. */
    public static TaskScheduler create(TaskBackend backend, TaskFailureHandler failureHandler) {
        return new ManagedTaskScheduler(Objects.requireNonNull(backend, "backend"),
                Objects.requireNonNull(failureHandler, "failureHandler"));
    }

    private static void reportUncaught(TaskFailure failure) {
        Thread thread = Thread.currentThread();
        Thread.UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
        if (handler != null) handler.uncaughtException(thread, failure.cause());
    }
}
