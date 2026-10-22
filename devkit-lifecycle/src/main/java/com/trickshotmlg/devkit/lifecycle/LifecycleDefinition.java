package com.trickshotmlg.devkit.lifecycle;

import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

/** Immutable, validated-at-build-time description of the services and components in one lifecycle. */
public final class LifecycleDefinition {
    private final Map<String, LifecycleNodeDefinition> nodes;
    private final List<LifecycleNodeDefinition> registrationOrder;
    private final List<LifecycleNodeDefinition> startupOrder;

    LifecycleDefinition(List<LifecycleNodeDefinition> definitions) {
        this.registrationOrder = List.copyOf(definitions);
        java.util.LinkedHashMap<String, LifecycleNodeDefinition> copy = new java.util.LinkedHashMap<>();
        for (LifecycleNodeDefinition definition : definitions) copy.put(definition.name(), definition);
        this.nodes = java.util.Collections.unmodifiableMap(copy);
        this.startupOrder = sort(copy, definitions);
    }

    /** Starts a new lifecycle definition builder. */
    public static LifecycleBuilder builder() { return new LifecycleBuilder(); }

    /** Registered node names in their stable registration order. */
    public List<String> registeredNames() { return registrationOrder.stream().map(LifecycleNodeDefinition::name).toList(); }

    /** Validated dependency order, with registration order as the stable tie-breaker. */
    public List<String> startupOrder() { return startupOrder.stream().map(LifecycleNodeDefinition::name).toList(); }

    Map<String, LifecycleNodeDefinition> nodes() { return nodes; }
    List<LifecycleNodeDefinition> registrationOrder() { return registrationOrder; }
    List<LifecycleNodeDefinition> startupOrderInternal() { return startupOrder; }

    private static List<LifecycleNodeDefinition> sort(Map<String, LifecycleNodeDefinition> nodes,
                                                       List<LifecycleNodeDefinition> registered) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<LifecycleNodeDefinition>> dependents = new HashMap<>();
        for (LifecycleNodeDefinition node : registered) {
            indegree.put(node.name(), node.dependencies().size());
            for (LifecycleDependency dependency : node.dependencies()) {
                if (node.name().equals(dependency.name())) {
                    throw new IllegalArgumentException("Lifecycle node '" + node.name() + "' cannot depend on itself");
                }
                LifecycleNodeDefinition target = nodes.get(dependency.name());
                if (target == null) {
                    throw new IllegalArgumentException("Lifecycle node '" + node.name()
                            + "' has missing dependency '" + dependency.name() + "'");
                }
                if (!sameIdentity(target.key(), dependency)) {
                    throw new IllegalArgumentException("Dependency '" + dependency.name()
                            + "' on lifecycle node '" + node.name() + "' uses an incompatible key type");
                }
                dependents.computeIfAbsent(dependency.name(), ignored -> new ArrayList<>()).add(node);
            }
        }
        PriorityQueue<LifecycleNodeDefinition> ready = new PriorityQueue<>(Comparator.comparingInt(LifecycleNodeDefinition::registrationOrder));
        for (LifecycleNodeDefinition node : registered) if (indegree.get(node.name()) == 0) ready.add(node);
        List<LifecycleNodeDefinition> sorted = new ArrayList<>(registered.size());
        while (!ready.isEmpty()) {
            LifecycleNodeDefinition next = ready.remove();
            sorted.add(next);
            for (LifecycleNodeDefinition dependent : dependents.getOrDefault(next.name(), List.of())) {
                int remaining = indegree.compute(dependent.name(), (ignored, value) -> value - 1);
                if (remaining == 0) ready.add(dependent);
            }
        }
        if (sorted.size() != registered.size()) {
            throw new IllegalArgumentException("Circular lifecycle dependency: " + cyclePath(nodes, registered));
        }
        return List.copyOf(sorted);
    }

    private static boolean sameIdentity(LifecycleDependency registered, LifecycleDependency requested) {
        if (registered instanceof ServiceKey<?> registeredService && requested instanceof ServiceKey<?> requestedService) {
            return registeredService.serviceType().equals(requestedService.serviceType());
        }
        return registered instanceof ComponentKey && requested instanceof ComponentKey;
    }

    private static String cyclePath(Map<String, LifecycleNodeDefinition> nodes,
                                     List<LifecycleNodeDefinition> registered) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();
        for (LifecycleNodeDefinition node : registered) {
            String found = visit(node.name(), nodes, visiting, visited, stack);
            if (found != null) return found;
        }
        return "unknown";
    }

    private static String visit(String name, Map<String, LifecycleNodeDefinition> nodes, Set<String> visiting,
                                Set<String> visited, ArrayDeque<String> stack) {
        if (visiting.contains(name)) {
            List<String> path = new ArrayList<>(stack);
            int start = path.indexOf(name);
            path = new ArrayList<>(path.subList(start, path.size()));
            path.add(name);
            return String.join(" -> ", path);
        }
        if (!visited.add(name)) return null;
        visiting.add(name);
        stack.addLast(name);
        for (LifecycleDependency dependency : nodes.get(name).dependencies()) {
            String result = visit(dependency.name(), nodes, visiting, visited, stack);
            if (result != null) return result;
        }
        stack.removeLast();
        visiting.remove(name);
        return null;
    }
}
