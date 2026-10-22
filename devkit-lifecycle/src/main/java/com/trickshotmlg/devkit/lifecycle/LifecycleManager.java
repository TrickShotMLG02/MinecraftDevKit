package com.trickshotmlg.devkit.lifecycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Runs one immutable service graph and owns its started services until reverse-order shutdown. */
public final class LifecycleManager implements AutoCloseable {
    private enum Operation { NONE, START, CLOSE, RELOAD }

    private final LifecycleDefinition definition;
    private final Object stateLock = new Object();
    private final Map<String, Object> services = new LinkedHashMap<>();
    private final Map<String, ServiceState> serviceStates = new LinkedHashMap<>();
    private final List<LifecycleNodeDefinition> started = new ArrayList<>();
    private LifecycleState state = LifecycleState.NEW;
    private Operation operation = Operation.NONE;
    private Thread operationOwner;

    private LifecycleManager(LifecycleDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition");
        for (LifecycleNodeDefinition node : definition.registrationOrder()) serviceStates.put(node.name(), ServiceState.NEW);
    }

    /** Fluent manager builder. */
    public static Builder builder() { return new Builder(); }
    /** Creates a manager from a previously validated immutable definition. */
    public static LifecycleManager create(LifecycleDefinition definition) { return new LifecycleManager(definition); }

    /** Starts nodes in validated dependency order. Startup is synchronous on the calling thread. */
    public void start() {
        begin(Operation.START);
        LifecycleNodeDefinition current = null;
        try {
            synchronized (stateLock) {
                if (state != LifecycleState.NEW) throw invalidTransition("start", state);
                state = LifecycleState.STARTING;
            }
            try {
                for (LifecycleNodeDefinition node : definition.startupOrderInternal()) {
                    current = node;
                    setNodeState(node.name(), ServiceState.STARTING);
                    LifecycleContext context = new LifecycleContext(this, node);
                    if (node.service()) {
                        Object value = node.factory().create(context);
                        if (value == null) throw new IllegalStateException("Factory for service '" + node.name() + "' returned null");
                        if (!node.serviceType().isInstance(value)) {
                            throw new IllegalStateException("Factory for service '" + node.name() + "' returned "
                                    + value.getClass().getName() + ", expected " + node.serviceType().getName());
                        }
                        synchronized (stateLock) { services.put(node.name(), value); }
                    } else {
                        node.start().run(context);
                    }
                    synchronized (stateLock) {
                        serviceStates.put(node.name(), ServiceState.RUNNING);
                        started.add(node);
                    }
                }
                synchronized (stateLock) { state = LifecycleState.RUNNING; }
            } catch (Throwable startupFailure) {
                setNodeState(current == null ? "" : current.name(), ServiceState.FAILED);
                List<Throwable> cleanupFailures = rollbackStarted();
                synchronized (stateLock) { state = LifecycleState.FAILED; }
                LifecycleException failure = new LifecycleException(LifecyclePhase.STARTUP,
                        current == null ? null : current.name(), "Failed to start lifecycle component '"
                        + (current == null ? "unknown" : current.name()) + "'", startupFailure);
                cleanupFailures.forEach(failure::addSuppressed);
                throw failure;
            }
        } finally {
            end(Operation.START);
        }
    }

    /** Retrieves a successfully started service. Lookup is only available while the manager is RUNNING. */
    public <T> T get(ServiceKey<T> key) {
        Objects.requireNonNull(key, "key");
        synchronized (stateLock) {
            ensureRunningForLookup();
            Object value = services.get(key.name());
            if (value == null) throw new IllegalArgumentException("No started service registered for key '" + key.name() + "'");
            return checkedValue(key, value);
        }
    }

    /** Optional typed lookup for a registered service while the manager is running. */
    public <T> Optional<T> find(ServiceKey<T> key) {
        Objects.requireNonNull(key, "key");
        synchronized (stateLock) {
            ensureRunningForLookup();
            Object value = services.get(key.name());
            return value == null ? Optional.empty() : Optional.of(checkedValue(key, value));
        }
    }

    public LifecycleState state() { synchronized (stateLock) { return state; } }
    public Map<String, ServiceState> serviceStates() {
        synchronized (stateLock) { return Collections.unmodifiableMap(new LinkedHashMap<>(serviceStates)); }
    }
    public List<String> startupOrder() { return definition.startupOrder(); }
    public List<String> shutdownOrder() {
        List<String> order = new ArrayList<>(definition.startupOrder());
        Collections.reverse(order);
        return List.copyOf(order);
    }

    @Override public void close() {
        begin(Operation.CLOSE);
        try {
            synchronized (stateLock) {
                if (state == LifecycleState.STOPPED || state == LifecycleState.FAILED) return;
                if (state == LifecycleState.NEW) { state = LifecycleState.STOPPED; return; }
                if (state != LifecycleState.RUNNING) throw invalidTransition("close", state);
                state = LifecycleState.STOPPING;
            }
            List<Throwable> failures = stopStarted();
            synchronized (stateLock) { state = LifecycleState.STOPPED; }
            if (!failures.isEmpty()) {
                Throwable primary = failures.get(0);
                failures.stream().skip(1).forEach(primary::addSuppressed);
                throw new LifecycleException(LifecyclePhase.SHUTDOWN, null,
                        "Lifecycle stopped with " + failures.size() + " cleanup failure(s)", primary);
            }
        } finally {
            end(Operation.CLOSE);
        }
    }

    LifecycleDefinition definition() { return definition; }

    <T> T requireDependency(LifecycleNodeDefinition requester, ServiceKey<T> key) {
        Objects.requireNonNull(key, "key");
        boolean declared = requester.dependencies().stream().anyMatch(dependency -> dependency.equals(key));
        if (!declared) throw new IllegalArgumentException("Lifecycle node '" + requester.name()
                + "' did not declare dependency '" + key.name() + "'");
        synchronized (stateLock) {
            Object value = services.get(key.name());
            if (value == null) throw new IllegalStateException("Dependency '" + key.name()
                    + "' is not started for lifecycle node '" + requester.name() + "'");
            return checkedValue(key, value);
        }
    }

    private <T> T checkedValue(ServiceKey<T> key, Object value) {
        LifecycleNodeDefinition node = definition.nodes().get(key.name());
        if (node == null || !(node.key() instanceof ServiceKey<?> registered)
                || !registered.serviceType().equals(key.serviceType()) || !key.serviceType().isInstance(value)) {
            throw new IllegalArgumentException("Service key '" + key.name() + "' has an incompatible type");
        }
        return key.serviceType().cast(value);
    }

    private void ensureRunningForLookup() {
        if (state != LifecycleState.RUNNING) throw new IllegalStateException("Services are unavailable while lifecycle is " + state);
    }

    private List<Throwable> rollbackStarted() {
        synchronized (stateLock) { state = LifecycleState.STOPPING; }
        List<Throwable> failures = stopStarted();
        synchronized (stateLock) { state = LifecycleState.FAILED; }
        return failures;
    }

    private List<Throwable> stopStarted() {
        List<LifecycleNodeDefinition> reverse;
        synchronized (stateLock) { reverse = new ArrayList<>(started); }
        Collections.reverse(reverse);
        List<Throwable> failures = new ArrayList<>();
        for (LifecycleNodeDefinition node : reverse) {
            setNodeState(node.name(), ServiceState.STOPPING);
            try {
                LifecycleContext context = new LifecycleContext(this, node);
                if (node.service()) {
                    Object value;
                    synchronized (stateLock) { value = services.get(node.name()); }
                    if (node.cleanup() != null) node.cleanup().close(value, context);
                    else if (value instanceof AutoCloseable closeable) closeable.close();
                } else if (node.stop() != null) node.stop().run(context);
                setNodeState(node.name(), ServiceState.STOPPED);
            } catch (Throwable error) {
                failures.add(new LifecycleException(LifecyclePhase.SHUTDOWN, node.name(),
                        "Failed to stop lifecycle component '" + node.name() + "'", error));
                setNodeState(node.name(), ServiceState.FAILED);
            } finally {
                synchronized (stateLock) {
                    services.remove(node.name());
                    started.remove(node);
                }
            }
        }
        return failures;
    }

    private void setNodeState(String name, ServiceState next) {
        synchronized (stateLock) { if (!name.isEmpty()) serviceStates.put(name, next); }
    }

    private void begin(Operation next) {
        boolean interrupted = false;
        synchronized (stateLock) {
            if (operationOwner == Thread.currentThread()) {
                throw new IllegalStateException("Reentrant lifecycle operation '" + next + "' during " + operation);
            }
            while (operation != Operation.NONE) {
                try { stateLock.wait(); }
                catch (InterruptedException ignored) { interrupted = true; }
            }
            operation = next;
            operationOwner = Thread.currentThread();
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private void end(Operation completed) {
        synchronized (stateLock) {
            if (operation == completed && operationOwner == Thread.currentThread()) {
                operation = Operation.NONE;
                operationOwner = null;
                stateLock.notifyAll();
            }
        }
    }

    private LifecycleException invalidTransition(String action, LifecycleState current) {
        return new LifecycleException(LifecyclePhase.STARTUP, null,
                "Cannot " + action + " lifecycle while it is " + current, null);
    }

    /** Builder convenience for creating a manager directly. */
    public static final class Builder {
        private final LifecycleBuilder delegate = LifecycleDefinition.builder();
        public <T> Builder service(ServiceKey<T> key, ServiceFactory<? extends T> factory,
                                   LifecycleDependency... dependencies) {
            delegate.service(key, factory, dependencies); return this;
        }
        public <T> Builder serviceWithCleanup(ServiceKey<T> key, ServiceFactory<? extends T> factory,
                                              ServiceCleanup<? super T> cleanup,
                                              LifecycleDependency... dependencies) {
            delegate.serviceWithCleanup(key, factory, cleanup, dependencies); return this;
        }
        public Builder component(ComponentKey key, LifecycleAction start, LifecycleAction stop,
                                 LifecycleDependency... dependencies) {
            delegate.component(key, start, stop, dependencies); return this;
        }
        public LifecycleManager build() { return LifecycleManager.create(delegate.build()); }
    }
}
