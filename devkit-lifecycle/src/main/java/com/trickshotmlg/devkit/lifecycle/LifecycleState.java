package com.trickshotmlg.devkit.lifecycle;

/** State of one lifecycle manager. A manager represents a single start/stop execution. */
public enum LifecycleState { NEW, STARTING, RUNNING, STOPPING, STOPPED, FAILED }
