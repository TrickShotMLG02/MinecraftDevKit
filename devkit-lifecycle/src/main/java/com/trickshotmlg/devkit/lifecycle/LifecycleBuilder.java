package com.trickshotmlg.devkit.lifecycle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Explicit builder for an immutable lifecycle dependency graph. */
public final class LifecycleBuilder {
    private final List<LifecycleNodeDefinition> nodes = new ArrayList<>();
    private final Set<String> names = new HashSet<>();
    private boolean built;

    /** Registers an automatically closed service with declared dependencies. */
    public <T> LifecycleBuilder service(ServiceKey<T> key, ServiceFactory<? extends T> factory,
                                        LifecycleDependency... dependencies) {
        return serviceWithCleanup(key, factory, null, dependencies);
    }

    /** Registers a service with an explicit cleanup callback. */
    public <T> LifecycleBuilder serviceWithCleanup(ServiceKey<T> key, ServiceFactory<? extends T> factory,
                                                  ServiceCleanup<? super T> cleanup,
                                                  LifecycleDependency... dependencies) {
        ensureMutable();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(dependencies, "dependencies");
        addName(key);
        @SuppressWarnings("unchecked") ServiceCleanup<Object> cleanupCast = (ServiceCleanup<Object>) cleanup;
        nodes.add(new LifecycleNodeDefinition(key, key.serviceType(), factory, cleanupCast, null, null, null,
                dependencyList(dependencies), nodes.size()));
        return this;
    }

    /** Registers a startup/shutdown component without a retrievable service value. */
    public LifecycleBuilder component(ComponentKey key, LifecycleAction start, LifecycleAction stop,
                                      LifecycleDependency... dependencies) {
        ensureMutable();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(dependencies, "dependencies");
        addName(key);
        nodes.add(new LifecycleNodeDefinition(key, null, null, null, start, stop, null,
                dependencyList(dependencies), nodes.size()));
        return this;
    }

    /** Builds an immutable definition. The builder cannot be reused afterward. */
    public LifecycleDefinition build() {
        ensureMutable();
        built = true;
        return new LifecycleDefinition(nodes);
    }

    private void addName(LifecycleDependency key) {
        if (!names.add(key.name())) throw new IllegalArgumentException("Duplicate lifecycle node name: " + key.name());
    }

    private static List<LifecycleDependency> dependencyList(LifecycleDependency[] dependencies) {
        List<LifecycleDependency> result = List.copyOf(Arrays.asList(dependencies.clone()));
        Set<String> unique = new HashSet<>();
        for (LifecycleDependency dependency : result) {
            Objects.requireNonNull(dependency, "dependency");
            if (!unique.add(dependency.name())) throw new IllegalArgumentException("Duplicate dependency: " + dependency.name());
        }
        return result;
    }

    private void ensureMutable() {
        if (built) throw new IllegalStateException("Lifecycle builder has already been built");
    }
}
