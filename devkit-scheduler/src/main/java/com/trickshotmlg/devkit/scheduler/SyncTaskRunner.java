package com.trickshotmlg.devkit.scheduler;

/** Tick-based tasks dispatched in the adapter's synchronous execution context. */
public interface SyncTaskRunner extends TaskRunner {
    /**
     * Schedules after one server tick; never invokes the callback inline.
     * @param action callback to run
     * @return task handle
     */
    TaskHandle nextTick(Runnable action);
    /**
     * Schedules after one server tick; never invokes the callback inline.
     * @param action callback to run
     * @return task handle
     */
    TaskHandle nextTick(TaskCallback action);
}
