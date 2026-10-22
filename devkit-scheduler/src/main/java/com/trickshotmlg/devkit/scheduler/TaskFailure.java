package com.trickshotmlg.devkit.scheduler;

import java.util.Objects;

/** Context for a callback failure reported to the scheduler's configured failure handler. */
public record TaskFailure(TaskHandle task, TaskExecutionTarget target, long executionCount, Throwable cause) {
    public TaskFailure {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(cause, "cause");
        if (executionCount < 1) throw new IllegalArgumentException("executionCount must be positive");
    }
}
