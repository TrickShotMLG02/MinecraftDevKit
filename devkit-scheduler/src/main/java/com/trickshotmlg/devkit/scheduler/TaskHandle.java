package com.trickshotmlg.devkit.scheduler;

import java.util.concurrent.CompletionStage;

/** Handle for observing and cancelling one scheduled task. */
public interface TaskHandle {
    long id();
    TaskState state();
    boolean cancel();
    /** Completes normally on success, or exceptionally on cancellation or failure. */
    CompletionStage<Void> completion();
    /** The original callback failure, when the task state is {@link TaskState#FAILED}. */
    Throwable failure();
}
