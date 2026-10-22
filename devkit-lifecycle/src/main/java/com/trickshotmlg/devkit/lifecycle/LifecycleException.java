package com.trickshotmlg.devkit.lifecycle;

import java.util.Objects;

/** Actionable failure from a lifecycle phase. Secondary cleanup failures are suppressed on the cause. */
public final class LifecycleException extends RuntimeException {
    private final LifecyclePhase phase;
    private final String componentName;

    /**
     * Creates a lifecycle failure with its phase and optional component identity.
     * @param phase lifecycle phase in which the failure occurred
     * @param componentName affected node name, or {@code null} for a manager-level failure
     * @param message actionable diagnostic
     * @param cause primary failure, if any
     */
    public LifecycleException(LifecyclePhase phase, String componentName, String message, Throwable cause) {
        super(message, cause);
        this.phase = Objects.requireNonNull(phase, "phase");
        this.componentName = componentName;
    }

    /** Phase in which the failure occurred. */
    public LifecyclePhase phase() { return phase; }
    /** Affected lifecycle node, or {@code null} for a manager-level failure. */
    public String componentName() { return componentName; }
}
