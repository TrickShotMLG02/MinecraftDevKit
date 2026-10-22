package com.trickshotmlg.devkit.scheduler;

/** Observable lifecycle state of a scheduled task. */
public enum TaskState {
    PENDING,
    RUNNING,
    ACTIVE,
    COMPLETED,
    CANCELLED,
    FAILED;

    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}
