package com.trickshotmlg.devkit.lifecycle;

import java.util.Objects;

/** Actionable failure from a lifecycle phase. Secondary cleanup failures are suppressed on the cause. */
public final class LifecycleException extends RuntimeException {
    private final LifecyclePhase phase;
    private final String componentName;

    public LifecycleException(LifecyclePhase phase, String componentName, String message, Throwable cause) {
        super(message, cause);
        this.phase = Objects.requireNonNull(phase, "phase");
        this.componentName = componentName;
    }

    public LifecyclePhase phase() { return phase; }
    public String componentName() { return componentName; }
}
