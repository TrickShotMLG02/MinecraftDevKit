package com.trickshotmlg.devkit.bukkit.permissions;

import com.trickshotmlg.devkit.permissions.DefaultAccess;
import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.permissions.PermissionCatalog;
import com.trickshotmlg.devkit.permissions.PermissionGroup;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.permissions.PermissionDefault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registers an immutable MinecraftDevKit permission catalog with Bukkit's permission manager. */
public final class BukkitPermissions {
    private static final Map<PluginManager, Map<String, OwnedByAdapter>> ADAPTER_OWNERS =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private BukkitPermissions() { }

    /**
     * Registers all ordinary permission nodes and catalog groups with the server. Existing
     * matching plugin.yml declarations are validated and reused; incompatible global conflicts
     * fail without changing the conflicting registration.
     *
     * <p>Registration and cleanup must run on Bukkit's primary server thread. The returned handle
     * also closes when the owning plugin is disabled.</p>
     *
     * @param plugin owning plugin
     * @param catalog immutable permission catalog
     * @return registration handle
     */
    public static BukkitPermissionRegistration register(Plugin plugin, PermissionCatalog catalog) {
        Objects.requireNonNull(plugin, "plugin");
        requireServerThread(Bukkit::isPrimaryThread, "register");
        return register(plugin, catalog, plugin.getServer().getPluginManager(), () -> true,
                plugin.getDescription().getPermissions().stream().map(org.bukkit.permissions.Permission::getName)
                        .collect(java.util.stream.Collectors.toSet()), true);
    }

    private static BukkitPermissionRegistration register(Plugin plugin, PermissionCatalog catalog,
                                                          PluginManager manager,
                                                          java.util.function.BooleanSupplier serverThread,
                                                          java.util.Set<String> pluginMetadataNodes,
                                                          boolean registerListener) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(manager, "manager");
        Objects.requireNonNull(serverThread, "serverThread");
        Objects.requireNonNull(pluginMetadataNodes, "pluginMetadataNodes");
        requireServerThread(serverThread, "register");

        List<PermissionDefinition> definitions = snapshot(catalog);
        List<BukkitPermissionRegistration.OwnedPermission> owned = new ArrayList<>();
        List<String> reusedMetadata = new ArrayList<>();
        List<String> reusedPreExisting = new ArrayList<>();
        try {
            for (PermissionDefinition definition : definitions) {
                org.bukkit.permissions.Permission existing = manager.getPermission(definition.node());
                if (existing != null) {
                    OwnedByAdapter currentOwner = ownerOf(manager, definition.node(), existing);
                    if (currentOwner != null && currentOwner.plugin() == plugin) {
                        throw new BukkitPermissionRegistrationException(definition.node(),
                                "Permission '" + definition.node() + "' is already owned by an active BukkitPermissions registration for this plugin");
                    }
                    if (!matches(existing, definition)) {
                        throw new BukkitPermissionRegistrationException(definition.node(),
                                "Permission '" + definition.node() + "' is already registered with different metadata");
                    }
                    if (pluginMetadataNodes.contains(definition.node())) reusedMetadata.add(definition.node());
                    else reusedPreExisting.add(definition.node());
                    continue;
                }
                org.bukkit.permissions.Permission permission = definition.toBukkit();
                try {
                    manager.addPermission(permission);
                    owned.add(new BukkitPermissionRegistration.OwnedPermission(definition.node(), permission));
                    recordOwner(manager, definition.node(), plugin, permission);
                } catch (RuntimeException failure) {
                    if (manager.getPermission(definition.node()) == permission) {
                        owned.add(new BukkitPermissionRegistration.OwnedPermission(definition.node(), permission));
                        recordOwner(manager, definition.node(), plugin, permission);
                    }
                    throw new BukkitPermissionRegistrationException(definition.node(),
                            "Could not register permission '" + definition.node() + "'", failure);
                }
            }
            BukkitPermissionRegistration registration = new BukkitPermissionRegistration(
                    plugin, manager, serverThread, owned,
                    new PermissionRegistrationDiagnostics(owned.size(), reusedMetadata.size() + reusedPreExisting.size(),
                            reusedMetadata, reusedPreExisting,
                            definitions.stream().map(PermissionDefinition::node).toList()));
            if (registerListener) {
                manager.registerEvents(registration, plugin);
                registration.listenerRegistered();
            }
            return registration;
        } catch (RuntimeException failure) {
            removeOwned(manager, owned);
            throw failure;
        }
    }

    private static List<PermissionDefinition> snapshot(PermissionCatalog catalog) {
        LinkedHashMap<String, PermissionDefinition> definitions = new LinkedHashMap<>();
        for (Permission permission : catalog.permissions()) {
            putUnique(definitions, new PermissionDefinition(permission.node(), permission.description(),
                    defaultValue(permission.defaultAccess()), Map.of()));
        }
        for (PermissionGroup group : catalog.groups()) {
            LinkedHashMap<String, Boolean> children = new LinkedHashMap<>();
            for (Permission permission : catalog.effectivePermissions(group)) {
                children.put(permission.node(), true);
            }
            putUnique(definitions, new PermissionDefinition(group.node(), group.description(),
                    defaultValue(group.defaultAccess()), children));
        }
        return List.copyOf(definitions.values());
    }

    private static void putUnique(Map<String, PermissionDefinition> definitions, PermissionDefinition definition) {
        if (definitions.putIfAbsent(definition.node(), definition) != null) {
            throw new IllegalArgumentException("Duplicate permission node in catalog snapshot: " + definition.node());
        }
    }

    private static PermissionDefault defaultValue(DefaultAccess access) {
        return switch (access) {
            case TRUE -> PermissionDefault.TRUE;
            case FALSE -> PermissionDefault.FALSE;
            case OP -> PermissionDefault.OP;
            case NOT_OP -> PermissionDefault.NOT_OP;
        };
    }

    private static boolean matches(org.bukkit.permissions.Permission existing, PermissionDefinition definition) {
        return existing.getDefault() == definition.defaultValue()
                && Objects.equals(normalize(existing.getDescription()), normalize(definition.description()))
                && existing.getChildren().equals(definition.children());
    }

    private static String normalize(String value) { return value == null || value.isEmpty() ? null : value; }

    private static void removeOwned(PluginManager manager, List<BukkitPermissionRegistration.OwnedPermission> owned) {
        for (int index = owned.size() - 1; index >= 0; index--) {
            BukkitPermissionRegistration.OwnedPermission registration = owned.get(index);
            if (manager.getPermission(registration.node()) == registration.permission()) {
                manager.removePermission(registration.permission());
            }
            removeOwner(manager, registration.node(), registration.permission());
        }
    }

    private static OwnedByAdapter ownerOf(PluginManager manager, String node,
                                           org.bukkit.permissions.Permission permission) {
        synchronized (ADAPTER_OWNERS) {
            OwnedByAdapter owner = ADAPTER_OWNERS.getOrDefault(manager, Map.of()).get(node);
            return owner != null && owner.permission() == permission ? owner : null;
        }
    }

    private static void recordOwner(PluginManager manager, String node, Plugin plugin,
                                    org.bukkit.permissions.Permission permission) {
        synchronized (ADAPTER_OWNERS) {
            ADAPTER_OWNERS.computeIfAbsent(manager, ignored -> new LinkedHashMap<>())
                    .put(node, new OwnedByAdapter(plugin, permission));
        }
    }

    static void removeOwner(PluginManager manager, String node, org.bukkit.permissions.Permission permission) {
        synchronized (ADAPTER_OWNERS) {
            Map<String, OwnedByAdapter> owners = ADAPTER_OWNERS.get(manager);
            if (owners == null) return;
            OwnedByAdapter owner = owners.get(node);
            if (owner != null && owner.permission() == permission) owners.remove(node);
            if (owners.isEmpty()) ADAPTER_OWNERS.remove(manager);
        }
    }

    private record OwnedByAdapter(Plugin plugin, org.bukkit.permissions.Permission permission) { }

    private static void requireServerThread(java.util.function.BooleanSupplier serverThread, String operation) {
        if (!serverThread.getAsBoolean()) {
            throw new IllegalStateException("Bukkit permission " + operation + " must run on the server thread");
        }
    }

    private record PermissionDefinition(String node, String description, PermissionDefault defaultValue,
                                        Map<String, Boolean> children) {
        private PermissionDefinition {
            Objects.requireNonNull(node, "node");
            Objects.requireNonNull(defaultValue, "defaultValue");
            children = Map.copyOf(new LinkedHashMap<>(children));
        }

        private org.bukkit.permissions.Permission toBukkit() {
            return new org.bukkit.permissions.Permission(node, description, defaultValue, children);
        }
    }

    static BukkitPermissionRegistration registerForTest(Plugin plugin, PermissionCatalog catalog,
                                                         PluginManager manager,
                                                         java.util.function.BooleanSupplier serverThread) {
        return register(plugin, catalog, manager, serverThread, java.util.Set.of(), false);
    }

    static BukkitPermissionRegistration registerForTest(Plugin plugin, PermissionCatalog catalog,
                                                         PluginManager manager,
                                                         java.util.function.BooleanSupplier serverThread,
                                                         java.util.Set<String> pluginMetadataNodes) {
        return register(plugin, catalog, manager, serverThread, pluginMetadataNodes, false);
    }
}
