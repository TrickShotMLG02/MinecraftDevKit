package com.trickshotmlg.devkit.lifecycle;

/** A named node that another lifecycle node may depend on. */
public sealed interface LifecycleDependency permits ServiceKey, ComponentKey {
    /** Stable logical name used for diagnostics and duplicate detection. */
    String name();
}
