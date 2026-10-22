package com.trickshotmlg.devkit.scheduler;

/** Observable lifecycle state of a scheduled task. */
public enum TaskState {
    /** Accepted by the managed layer but callback has not begun. */
    PENDING,
    /** Callback is currently executing. */
    RUNNING,
    /** Repeating task is waiting for a later invocation. */
    ACTIVE,
    /** One-shot callback completed successfully. */
    COMPLETED,
    /** Cancellation was accepted before terminal completion. */
    CANCELLED,
    /** Scheduling or callback failed terminally. */
    FAILED;

    /**
     * Returns whether this state can no longer transition.
     * @return true for completed, cancelled, or failed states
     */
    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}
