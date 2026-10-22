package com.trickshotmlg.devkit.lifecycle;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

/** Immutable point-in-time lifecycle state and dependency-order diagnostics.
 * @param state manager state at capture time
 * @param components per-node states keyed by name
 * @param startupOrder deterministic startup order
 * @param shutdownOrder reverse dependency shutdown order
 */
public record LifecycleDiagnostics(LifecycleState state, Map<String, ServiceState> components,
                                   List<String> startupOrder, List<String> shutdownOrder) {
    public LifecycleDiagnostics {
        components = Collections.unmodifiableMap(new LinkedHashMap<>(components));
        startupOrder = List.copyOf(startupOrder);
        shutdownOrder = List.copyOf(shutdownOrder);
    }
}
