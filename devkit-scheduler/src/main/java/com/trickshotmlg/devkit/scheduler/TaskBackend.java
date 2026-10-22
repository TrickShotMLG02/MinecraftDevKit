package com.trickshotmlg.devkit.scheduler;

/**
 * Platform scheduling SPI. Backends map the explicit target and tick schedule to native APIs.
 * Calls may originate from any thread supported by the backend; task callbacks are dispatched
 * by the backend according to the requested target.
 */
@FunctionalInterface
public interface TaskBackend {
    BackendTask schedule(TaskExecutionTarget target, TaskSchedule schedule, Runnable callback);
}
