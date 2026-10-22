package com.trickshotmlg.devkit.lifecycle;

/** State of one registered service or lifecycle component. */
public enum ServiceState {
    /** Not yet started. */ NEW,
    /** Startup callback is executing. */ STARTING,
    /** Startup succeeded and the node is available. */ RUNNING,
    /** Cleanup callback is executing. */ STOPPING,
    /** Cleanup completed successfully or the node was rolled back. */ STOPPED,
    /** Startup or cleanup failed. */ FAILED
}
