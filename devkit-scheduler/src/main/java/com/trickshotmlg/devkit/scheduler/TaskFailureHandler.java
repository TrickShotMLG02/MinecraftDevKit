package com.trickshotmlg.devkit.scheduler;

/** Receives task callback failures. Implementations must not assume a specific callback thread. */
@FunctionalInterface
public interface TaskFailureHandler {
    void onFailure(TaskFailure failure);
}
