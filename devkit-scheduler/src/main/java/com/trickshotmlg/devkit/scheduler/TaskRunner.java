package com.trickshotmlg.devkit.scheduler;

import java.time.Duration;

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
     * Convenience overload; positive durations are rounded up to the next server tick at 20 TPS.
     * This is a tick delay, not a wall-clock deadline; lag may make elapsed time longer.
     * @param delay non-negative requested delay
     * @param action callback to run
     * @return task handle
     */
    default TaskHandle later(Duration delay, Runnable action) { return later(TickDelay.of(delay), action); }
    /**
     * TaskCallback overload of {@link #later(Duration, Runnable)}.
     * @param delay non-negative requested delay
     * @param action callback to run
     * @return task handle
     */
    default TaskHandle later(Duration delay, TaskCallback action) { return later(TickDelay.of(delay), action); }
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
     * Convenience overload converting both durations to 20 TPS ticks with ceiling rounding.
     * @param initialDelay non-negative delay before first run
     * @param period positive interval
     * @param action callback to run
     * @return cancellable task handle
     */
    default TaskHandle repeat(Duration initialDelay, Duration period, Runnable action) {
        return repeat(TickDelay.of(initialDelay), TickPeriod.of(period), action);
    }
    /**
     * TaskCallback overload of {@link #repeat(Duration, Duration, Runnable)}.
     * @param initialDelay non-negative delay before first run
     * @param period positive interval
     * @param action callback to run
     * @return cancellable task handle
     */
    default TaskHandle repeat(Duration initialDelay, Duration period, TaskCallback action) {
        return repeat(TickDelay.of(initialDelay), TickPeriod.of(period), action);
    }
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
