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
    private boolean acceptingResources;

    LifecycleContext(LifecycleManager manager, LifecycleNodeDefinition node) {
        this(manager, node, true);
    }

    LifecycleContext(LifecycleManager manager, LifecycleNodeDefinition node, boolean resourceRegistrationAllowed) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.node = Objects.requireNonNull(node, "node");
        this.acceptingResources = resourceRegistrationAllowed;
    }

    /** Name of the service or component whose callback is running. */
    public String componentName() { return node.name(); }

    /**
     * Gets a declared service dependency that has started successfully.
     * @param key dependency key declared when registering this node
     * @param <T> service type
     * @return started dependency instance
     * @throws IllegalArgumentException if the dependency was not declared
     * @throws IllegalStateException if the dependency is not yet started
     */
    public <T> T require(ServiceKey<T> key) { return manager.requireDependency(node, key); }

    /**
     * Gets the current service itself, for example from its reload hook.
     * @param key this node's service key
     * @param <T> service type
     * @return published service instance
     * @throws IllegalArgumentException if the key is not this node's service key
     * @throws IllegalStateException if the current service is not published
     */
    public <T> T service(ServiceKey<T> key) { return manager.requireCurrentService(node, key); }

    /**
     * Registers a resource for automatic close. It is cleaned if startup fails or when this node stops.
     * @param resource resource owned by this node after successful startup
     * @param <T> resource type
     * @return the same resource, for convenient inline registration
     * @throws IllegalArgumentException if this lifecycle already owns the same resource identity
     * @throws IllegalStateException if registration is attempted outside this node's startup callback
     */
    public <T extends AutoCloseable> T manage(T resource) {
        Objects.requireNonNull(resource, "resource");
        synchronized (resourceLock) {
            if (!acceptingResources) throw new IllegalStateException("Resource registration has ended for '" + componentName() + "'");
            resources.add(manager.claimResource(resource));
        }
        return resource;
    }

    /** Ensures an automatically closeable service result is owned without duplicating ownership
     * when the factory already registered that exact instance for rollback safety. */
    void manageServiceResult(AutoCloseable resource) {
        synchronized (resourceLock) {
            if (!acceptingResources) throw new IllegalStateException("Resource registration has ended for '" + componentName() + "'");
            if (resources.stream().anyMatch(owned -> owned.owns(resource))) return;
            resources.add(manager.claimResource(resource));
        }
    }

    /**
     * Registers a cleanup action, invoked at most once during rollback or shutdown.
     * @param cleanup cleanup callback with lifecycle context access
     */
    public void onClose(LifecycleAction cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");
        manage((AutoCloseable) () -> cleanup.run(this));
    }

    /**
     * Registers a simple cleanup action, invoked at most once during rollback or shutdown.
     * @param cleanup cleanup callback
     */
    public void onClose(Runnable cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");
        manage((AutoCloseable) cleanup::run);
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
