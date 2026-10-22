package com.trickshotmlg.devkit.scheduler;

import java.util.Objects;

/** Failure reported by a task or by the backend while scheduling it.
 * @param task failed task handle
 * @param target requested execution context
 * @param executionCount number of user callback invocations begun; zero means backend scheduling failed first
 * @param cause original scheduling or callback failure
 */
public record TaskFailure(TaskHandle task, TaskExecutionTarget target, long executionCount, Throwable cause) {
    /** Validates failure context.
     * @param task failed task handle
     * @param target requested execution context
     * @param executionCount callbacks begun before failure
     * @param cause original failure
     */
    public TaskFailure {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(cause, "cause");
        if (executionCount < 0) throw new IllegalArgumentException("executionCount must not be negative");
    }
}
