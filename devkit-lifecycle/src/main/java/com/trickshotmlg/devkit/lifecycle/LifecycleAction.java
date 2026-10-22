package com.trickshotmlg.devkit.lifecycle;

/** Startup, shutdown, or reload action for a lifecycle component. */
@FunctionalInterface
public interface LifecycleAction {
    void run(LifecycleContext context) throws Exception;
}
