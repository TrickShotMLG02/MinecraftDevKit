package com.trickshotmlg.devkit.scheduler;

/** A task callback that may inspect and cancel its own execution. */
@FunctionalInterface
public interface TaskCallback {
    /** Invokes the callback.
     * @param context context for this invocation
     * @throws Exception when the callback fails
     */
    void run(TaskContext context) throws Exception;
}
