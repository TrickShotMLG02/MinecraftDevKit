package com.trickshotmlg.devkit.scheduler;

/** Native task cancellation supplied by a scheduling backend. */
@FunctionalInterface
public interface BackendTask {
    /** Requests native cancellation. It may not interrupt a callback already running. */
    void cancel();
}
