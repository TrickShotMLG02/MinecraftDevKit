package com.trickshotmlg.devkit.scheduler;

/**
 * Platform scheduling SPI. Backends map the explicit target and tick schedule to native APIs.
 * Calls may originate from any thread supported by the backend; task callbacks are dispatched
 * by the backend according to the requested target. A backend may dispatch before {@code schedule}
 * returns its native cancellation handle. The managed layer is safe for that ordering and rejects
 * callbacks after cancellation or terminal completion.
 */
@FunctionalInterface
public interface TaskBackend {
    /**
     * Schedules one managed callback. The managed wrapper tolerates the callback running before this method returns.
     * @param target execution context requested by the caller
     * @param schedule tick schedule
     * @param callback guarded callback to dispatch
     * @return native cancellation action
     */
    BackendTask schedule(TaskExecutionTarget target, TaskSchedule schedule, Runnable callback);
}
