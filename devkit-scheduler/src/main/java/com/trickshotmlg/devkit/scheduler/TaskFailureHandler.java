package com.trickshotmlg.devkit.scheduler;

/** Receives scheduling and task callback failures. Implementations must not assume a specific callback thread. */
@FunctionalInterface
public interface TaskFailureHandler {
    /**
     * Reports the failure after the task state transition has been recorded.
     * @param failure scheduling or callback failure
     */
    void onFailure(TaskFailure failure);
}
