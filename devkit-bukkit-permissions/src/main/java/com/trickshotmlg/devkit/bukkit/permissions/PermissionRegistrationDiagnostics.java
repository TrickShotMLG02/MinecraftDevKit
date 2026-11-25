package com.trickshotmlg.devkit.bukkit.permissions;

import java.util.List;

/** Immutable summary of a catalog registration.
 * @param registered number of nodes newly registered by this call
 * @param reused number of matching nodes that were already present
 * @param reusedPluginMetadata nodes also declared in the owning plugin descriptor
 * @param reusedPreExisting matching nodes with no declaration in the owning plugin descriptor
 * @param nodes complete catalog node list represented by the registration
 */
public record PermissionRegistrationDiagnostics(int registered, int reused,
                                               List<String> reusedPluginMetadata,
                                               List<String> reusedPreExisting,
                                               List<String> nodes) {
    public PermissionRegistrationDiagnostics {
        if (registered < 0 || reused < 0) throw new IllegalArgumentException("counts must not be negative");
        reusedPluginMetadata = List.copyOf(reusedPluginMetadata);
        reusedPreExisting = List.copyOf(reusedPreExisting);
        nodes = List.copyOf(nodes);
        if (reused != reusedPluginMetadata.size() + reusedPreExisting.size()) {
            throw new IllegalArgumentException("reused count must match its source lists");
        }
    }
}
