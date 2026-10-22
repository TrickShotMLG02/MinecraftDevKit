package com.trickshotmlg.devkit.scheduler;

/** Immutable task execution information with a cancellation operation. */
public interface TaskContext {
    TaskHandle task();
    TaskExecutionTarget target();
    TaskScope scope();
    long executionCount();
    void cancel();
}
