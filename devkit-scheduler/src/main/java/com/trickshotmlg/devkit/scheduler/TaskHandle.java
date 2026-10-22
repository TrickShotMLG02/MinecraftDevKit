package com.trickshotmlg.devkit.scheduler;

import java.util.concurrent.CompletionStage;

/** Handle for observing and cancelling one scheduled task. */
public interface TaskHandle {
    long id();
    TaskState state();
    boolean cancel();
    /** Completes normally on success, or exceptionally on cancellation or failure. */
    CompletionStage<Void> completion();
    /** The most recent callback failure, or {@code null} if none was reported. */
    Throwable failure();
}
