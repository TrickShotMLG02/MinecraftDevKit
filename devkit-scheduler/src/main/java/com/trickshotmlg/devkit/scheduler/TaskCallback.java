package com.trickshotmlg.devkit.scheduler;

/** A task callback that may inspect and cancel its own execution. */
@FunctionalInterface
public interface TaskCallback {
    void run(TaskContext context) throws Exception;
}
