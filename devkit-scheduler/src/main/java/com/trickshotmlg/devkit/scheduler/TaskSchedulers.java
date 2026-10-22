package com.trickshotmlg.devkit.scheduler;

import java.util.Objects;

/** Factories for managed schedulers backed by a platform scheduling implementation. */
public final class TaskSchedulers {
    private TaskSchedulers() { }

    /**
     * Creates a scheduler that reports failures to the current thread's uncaught exception handler.
     * @param backend platform scheduling implementation
     * @return managed scheduler
     */
    public static TaskScheduler create(TaskBackend backend) {
        return create(backend, TaskSchedulers::reportUncaught);
    }

    /**
     * Creates a scheduler that owns tasks submitted to {@code backend}.
     * @param backend platform scheduling implementation
     * @param failureHandler receives callback and backend failures; a throwing handler is reported to the thread's uncaught handler
     * @return managed scheduler
     */
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
