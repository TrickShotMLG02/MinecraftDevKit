package com.trickshotmlg.devkit.lifecycle;

/** Startup, shutdown, or reload callback, run synchronously on the thread invoking the manager. */
@FunctionalInterface
public interface LifecycleAction {
    /** Performs the operation for the current node. */
    void run(LifecycleContext context) throws Exception;
}
