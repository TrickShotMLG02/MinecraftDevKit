package com.trickshotmlg.devkit.scheduler;

/** Common tick-based scheduling operations for one execution target. */
public interface TaskRunner {
    TaskHandle run(Runnable action);
    TaskHandle run(TaskCallback action);
    TaskHandle later(TickDelay delay, Runnable action);
    TaskHandle later(TickDelay delay, TaskCallback action);
    TaskHandle repeat(TickDelay initialDelay, TickPeriod period, Runnable action);
    TaskHandle repeat(TickDelay initialDelay, TickPeriod period, TaskCallback action);
    /** Repeats with an explicit exception policy; one-shot failure behavior is always terminal. */
    TaskHandle repeat(TickDelay initialDelay, TickPeriod period, TaskFailurePolicy failurePolicy, TaskCallback action);
}
