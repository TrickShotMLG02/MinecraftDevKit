package com.trickshotmlg.devkit.scheduler;

import java.util.concurrent.CompletionStage;

/** Handle for observing and cancelling one scheduled task. */
public interface TaskHandle {
    /**
     * Stable identifier unique within one scheduler.
     * @return task identifier
     */
    long id();
    /**
     * Current observable lifecycle state.
     * @return current state
     */
    TaskState state();
    /**
     * Requests cancellation; returns false if the task is already terminal.
     * @return true if cancellation was accepted
     */
    boolean cancel();
    /**
     * One-shot completion; for a repeating task this remains pending until cancellation or failure.
     * Cancellation and failure complete the stage exceptionally.
     * @return task completion stage
     */
    CompletionStage<Void> completion();
    /**
     * The most recent callback failure, or {@code null} if none was reported.
     * @return most recent failure
     */
    Throwable failure();
}
