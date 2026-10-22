package com.trickshotmlg.devkit.scheduler;

/** Common tick-based scheduling operations for one execution target. */
public interface TaskRunner {
    /**
     * Schedules as soon as the backend can dispatch; Bukkit dispatches sync work on a later server tick.
     * @param action callback to run
     * @return task handle
     */
    TaskHandle run(Runnable action);
    /**
     * Schedules as soon as the backend can dispatch.
     * @param action callback to run
     * @return task handle
     */
    TaskHandle run(TaskCallback action);
    /**
     * Schedules once after the specified server-tick delay.
     * @param delay server ticks before dispatch
     * @param action callback to run
     * @return task handle
     */
    TaskHandle later(TickDelay delay, Runnable action);
    /**
     * Schedules once after the specified server-tick delay.
     * @param delay server ticks before dispatch
     * @param action callback to run
     * @return task handle
     */
    TaskHandle later(TickDelay delay, TaskCallback action);
    /**
     * Schedules repeatedly using the default cancel-on-failure policy.
     * @param initialDelay ticks before first dispatch
     * @param period ticks between dispatches
     * @param action callback to run
     * @return task handle
     */
    TaskHandle repeat(TickDelay initialDelay, TickPeriod period, Runnable action);
    /**
     * Schedules repeatedly using the default cancel-on-failure policy.
     * @param initialDelay ticks before first dispatch
     * @param period ticks between dispatches
     * @param action callback to run
     * @return task handle
     */
    TaskHandle repeat(TickDelay initialDelay, TickPeriod period, TaskCallback action);
    /**
     * Repeats with an explicit exception policy; one-shot failure behavior is always terminal.
     * Repeating invocations do not overlap in the managed layer. If an async invocation is still
     * running when the backend dispatches again, that dispatch is skipped.
     * @param initialDelay ticks before the first invocation
     * @param period ticks between subsequent invocations
     * @param failurePolicy behavior after callback exceptions
     * @param action callback to invoke
     * @return handle for observing and cancelling the task
     */
    TaskHandle repeat(TickDelay initialDelay, TickPeriod period, TaskFailurePolicy failurePolicy, TaskCallback action);
}
