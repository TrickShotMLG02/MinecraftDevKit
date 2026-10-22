package com.trickshotmlg.devkit.lifecycle;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/** Restricted context supplied to lifecycle callbacks. Only declared, initialized dependencies are visible. */
public final class LifecycleContext {
    private final LifecycleManager manager;
    private final LifecycleNodeDefinition node;
    private final Object resourceLock = new Object();
    private final List<LifecycleManager.OwnedResource> resources = new ArrayList<>();
    private boolean acceptingResources = true;

    LifecycleContext(LifecycleManager manager, LifecycleNodeDefinition node) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.node = Objects.requireNonNull(node, "node");
    }

    /** Name of the service or component whose callback is running. */
    public String componentName() { return node.name(); }

    /** Gets a declared service dependency that has started successfully. */
    public <T> T require(ServiceKey<T> key) { return manager.requireDependency(node, key); }

    /** Registers a resource for automatic close. It is cleaned if startup fails or when this node stops. */
    public <T extends AutoCloseable> T manage(T resource) {
        Objects.requireNonNull(resource, "resource");
        synchronized (resourceLock) {
            if (!acceptingResources) throw new IllegalStateException("Resource registration has ended for '" + componentName() + "'");
            resources.add(manager.claimResource(resource));
        }
        return resource;
    }

    /** Registers a cleanup action, invoked at most once during rollback or shutdown. */
    public void onClose(LifecycleAction cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");
        manage((AutoCloseable) () -> cleanup.run(this));
    }

    List<LifecycleManager.OwnedResource> transferResources() {
        synchronized (resourceLock) {
            acceptingResources = false;
            List<LifecycleManager.OwnedResource> transferred = List.copyOf(resources);
            resources.clear();
            return transferred;
        }
    }

    List<Throwable> closeResources() {
        List<LifecycleManager.OwnedResource> reverse;
        synchronized (resourceLock) {
            acceptingResources = false;
            reverse = new ArrayList<>(resources);
            resources.clear();
        }
        return manager.closeResources(reverse);
    }
}
