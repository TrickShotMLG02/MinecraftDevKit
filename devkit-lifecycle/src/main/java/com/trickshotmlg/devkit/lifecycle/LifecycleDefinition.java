package com.trickshotmlg.devkit.lifecycle;

import java.util.List;
import java.util.Map;

/** Immutable, validated-at-build-time description of the services and components in one lifecycle. */
public final class LifecycleDefinition {
    private final Map<String, LifecycleNodeDefinition> nodes;
    private final List<LifecycleNodeDefinition> registrationOrder;

    LifecycleDefinition(List<LifecycleNodeDefinition> definitions) {
        this.registrationOrder = List.copyOf(definitions);
        java.util.LinkedHashMap<String, LifecycleNodeDefinition> copy = new java.util.LinkedHashMap<>();
        for (LifecycleNodeDefinition definition : definitions) copy.put(definition.name(), definition);
        this.nodes = java.util.Collections.unmodifiableMap(copy);
    }

    /** Starts a new lifecycle definition builder. */
    public static LifecycleBuilder builder() { return new LifecycleBuilder(); }

    /** Registered node names in their stable registration order. */
    public List<String> registeredNames() { return registrationOrder.stream().map(LifecycleNodeDefinition::name).toList(); }

    Map<String, LifecycleNodeDefinition> nodes() { return nodes; }
    List<LifecycleNodeDefinition> registrationOrder() { return registrationOrder; }
}
