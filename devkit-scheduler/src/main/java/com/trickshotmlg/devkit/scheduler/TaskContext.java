package com.trickshotmlg.devkit.scheduler;

/** Immutable task execution information with a cancellation operation. */
public interface TaskContext {
    /**
     * Handle for the currently executing task.
     * @return task handle
     */
    TaskHandle task();
    /**
     * Context on which this callback was dispatched.
     * @return adapter execution target
     */
    TaskExecutionTarget target();
    /**
     * Owner of this task.
     * @return owning scope
     */
    TaskScope scope();
    /**
     * One-based count of callback invocations begun for this task.
     * @return invocation count
     */
    long executionCount();
    /** Requests cancellation of this task. */
    void cancel();
}
