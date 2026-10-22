package com.trickshotmlg.devkit.lifecycle;

import java.util.Objects;

/** Restricted context supplied to lifecycle callbacks. Only declared, initialized dependencies are visible. */
public final class LifecycleContext {
    private final LifecycleManager manager;
    private final LifecycleNodeDefinition node;

    LifecycleContext(LifecycleManager manager, LifecycleNodeDefinition node) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.node = Objects.requireNonNull(node, "node");
    }

    /** Name of the service or component whose callback is running. */
    public String componentName() { return node.name(); }

    /** Gets a declared service dependency that has started successfully. */
    public <T> T require(ServiceKey<T> key) { return manager.requireDependency(node, key); }
}
