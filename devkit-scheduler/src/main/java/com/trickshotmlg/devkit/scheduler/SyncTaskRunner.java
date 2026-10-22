package com.trickshotmlg.devkit.scheduler;

/** Tick-based tasks dispatched in the adapter's synchronous execution context. */
public interface SyncTaskRunner extends TaskRunner {
    /** Schedules for the next server tick; never invokes the callback inline. */
    TaskHandle nextTick(Runnable action);
    TaskHandle nextTick(TaskCallback action);
}
