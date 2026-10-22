package com.trickshotmlg.devkit.lifecycle;

/** State of one lifecycle manager. A manager represents a single start/stop execution. */
public enum LifecycleState {
    /** Not yet started. */ NEW,
    /** Startup is in progress. */ STARTING,
    /** All nodes started successfully. */ RUNNING,
    /** Rollback or shutdown is in progress. */ STOPPING,
    /** Shutdown completed; cleanup failures are reported separately. */ STOPPED,
    /** Startup failed after rollback was attempted. */ FAILED
}
