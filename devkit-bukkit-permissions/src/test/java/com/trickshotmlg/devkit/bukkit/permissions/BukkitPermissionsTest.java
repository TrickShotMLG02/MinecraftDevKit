package com.trickshotmlg.devkit.bukkit.permissions;

import com.trickshotmlg.devkit.permissions.DefaultAccess;
import com.trickshotmlg.devkit.permissions.PermissionCatalog;
import com.trickshotmlg.devkit.permissions.PermissionNamespace;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.event.server.PluginDisableEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BukkitPermissionsTest {
    private final PermissionCatalog catalog = catalog();

    @Test void registersDefinitionsDefaultsAndResolvedPositiveGroupChildren() {
        FakePluginManager state = new FakePluginManager();
        BukkitPermissionRegistration registration = register(state, () -> true);

        assertEquals(4, registration.diagnostics().registered());
        assertEquals(PermissionDefault.TRUE, state.permissions.get("sample.read").getDefault());
        assertEquals(PermissionDefault.FALSE, state.permissions.get("sample.write").getDefault());
        assertEquals(PermissionDefault.OP, state.permissions.get("sample.base").getDefault());
        assertEquals(PermissionDefault.NOT_OP, state.permissions.get("sample.curator").getDefault());
        assertEquals(Map.of("sample.read", true, "sample.write", true), state.permissions.get("sample.base").getChildren());
        assertEquals(Map.of("sample.read", true), state.permissions.get("sample.curator").getChildren());
        assertFalse(state.permissions.get("sample.curator").getChildren().containsKey("sample.write"),
                "exclusions must omit a grant and never create a negative child assignment");
        registration.close();
    }

    @Test void reusesMatchingMetadataDeclarationsWithoutClaimingOwnership() {
        FakePluginManager state = new FakePluginManager();
        state.permissions.put("sample.read", new org.bukkit.permissions.Permission(
                "sample.read", "Read data", PermissionDefault.TRUE));
        BukkitPermissionRegistration registration = register(state, () -> true);
        assertEquals(1, registration.diagnostics().reused());
        assertEquals(java.util.List.of("sample.read"), registration.diagnostics().reusedPreExisting());
        assertFalse(registration.ownedNodes().contains("sample.read"));
        registration.close();
        assertNotNull(state.permissions.get("sample.read"));
    }

    @Test void identifiesMatchingPluginDescriptorMetadataSeparately() {
        FakePluginManager state = new FakePluginManager();
        state.permissions.put("sample.read", new org.bukkit.permissions.Permission(
                "sample.read", "Read data", PermissionDefault.TRUE));
        Plugin plugin = proxy(Plugin.class, (method, args) -> null);
        BukkitPermissionRegistration registration = BukkitPermissions.registerForTest(plugin, catalog,
                state.proxy(), () -> true, java.util.Set.of("sample.read"));
        assertEquals(java.util.List.of("sample.read"), registration.diagnostics().reusedPluginMetadata());
        assertTrue(registration.diagnostics().reusedPreExisting().isEmpty());
        registration.close();
    }

    @Test void rejectsDuplicateActiveRegistrationOwnedBySamePlugin() {
        FakePluginManager state = new FakePluginManager();
        PluginManager manager = state.proxy();
        Plugin plugin = proxy(Plugin.class, (method, args) -> null);
        BukkitPermissionRegistration first = BukkitPermissions.registerForTest(plugin, catalog, manager, () -> true);
        BukkitPermissionRegistrationException failure = assertThrows(BukkitPermissionRegistrationException.class,
                () -> BukkitPermissions.registerForTest(plugin, catalog, manager, () -> true));
        assertTrue(failure.getMessage().contains("already owned"));
        first.close();
    }

    @Test void matchingPermissionOwnedByAnotherAdapterIsReusedWithoutOwnershipTransfer() {
        FakePluginManager state = new FakePluginManager();
        PluginManager manager = state.proxy();
        BukkitPermissionRegistration first = BukkitPermissions.registerForTest(
                proxy(Plugin.class, (method, args) -> null), catalog, manager, () -> true);
        BukkitPermissionRegistration second = BukkitPermissions.registerForTest(
                proxy(Plugin.class, (method, args) -> null), catalog, manager, () -> true);
        assertEquals(4, second.diagnostics().reusedPreExisting().size());
        assertTrue(second.ownedNodes().isEmpty());
        second.close();
        assertEquals(4, state.permissions.size());
        first.close();
        assertTrue(state.permissions.isEmpty());
    }

    @Test void pluginDisableClosesOwnedRegistration() {
        FakePluginManager state = new FakePluginManager();
        Plugin plugin = proxy(Plugin.class, (method, args) -> null);
        BukkitPermissionRegistration registration = BukkitPermissions.registerForTest(plugin, catalog,
                state.proxy(), () -> true);
        registration.onPluginDisable(new PluginDisableEvent(plugin));
        assertTrue(registration.isClosed());
        assertTrue(state.permissions.isEmpty());
    }

    @Test void rejectsConflictingGlobalPermissionAndDoesNotMutateIt() {
        FakePluginManager state = new FakePluginManager();
        org.bukkit.permissions.Permission foreign = new org.bukkit.permissions.Permission(
                "sample.read", "Other meaning", PermissionDefault.OP);
        state.permissions.put(foreign.getName(), foreign);
        BukkitPermissionRegistrationException failure = assertThrows(BukkitPermissionRegistrationException.class,
                () -> register(state, () -> true));
        assertEquals("sample.read", failure.permissionNode());
        assertSame(foreign, state.permissions.get("sample.read"));
        assertFalse(state.permissions.containsKey("sample.write"), "partial owned additions must roll back");
    }

    @Test void cleanupIsIdempotentAndRemovesOnlyStillOwnedObjects() {
        FakePluginManager state = new FakePluginManager();
        BukkitPermissionRegistration registration = register(state, () -> true);
        org.bukkit.permissions.Permission replacement = new org.bukkit.permissions.Permission(
                "sample.read", "replacement", PermissionDefault.FALSE);
        state.permissions.put("sample.read", replacement);
        registration.close();
        registration.close();
        assertTrue(registration.isClosed());
        assertSame(replacement, state.permissions.get("sample.read"));
        assertFalse(state.permissions.isEmpty());
        assertFalse(state.permissions.containsKey("sample.write"));
    }

    @Test void partialAddFailureRollsBackEarlierOwnedRegistrations() {
        FakePluginManager state = new FakePluginManager();
        state.failOnAdd.set("sample.write");
        BukkitPermissionRegistrationException failure = assertThrows(BukkitPermissionRegistrationException.class,
                () -> register(state, () -> true));
        assertEquals("sample.write", failure.permissionNode());
        assertTrue(state.permissions.isEmpty());
    }

    @Test void requiresServerThreadBeforeAnyMutation() {
        FakePluginManager state = new FakePluginManager();
        assertThrows(IllegalStateException.class, () -> register(state, () -> false));
        assertTrue(state.permissions.isEmpty());
    }

    private static BukkitPermissionRegistration register(FakePluginManager state,
                                                           java.util.function.BooleanSupplier thread) {
        return BukkitPermissions.registerForTest(proxy(Plugin.class, (method, args) -> null), catalog(),
                state.proxy(), thread);
    }

    private static PermissionCatalog catalog() {
        PermissionNamespace ns = PermissionNamespace.create("sample");
        var read = ns.permission("read", options -> options.description("Read data")
                .defaultAccess(DefaultAccess.TRUE));
        var write = ns.permission("write", options -> options.defaultAccess(DefaultAccess.FALSE));
        var base = ns.group("base", group -> group.defaultAccess(DefaultAccess.OP).grant(read, write));
        ns.group("curator", group -> group.defaultAccess(DefaultAccess.NOT_OP).inherits(base).exclude(write));
        return ns.build();
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "TestProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }
            return invocation.invoke(method.getName(), args == null ? new Object[0] : args);
        }));
    }

    @FunctionalInterface private interface Invocation { Object invoke(String method, Object[] args); }

    private static final class FakePluginManager {
        private final Map<String, org.bukkit.permissions.Permission> permissions = new LinkedHashMap<>();
        private final AtomicReference<String> failOnAdd = new AtomicReference<>();
        private PluginManager proxy() {
            return BukkitPermissionsTest.proxy(PluginManager.class, (method, args) -> switch (method) {
                case "getPermission" -> permissions.get(args[0]);
                case "addPermission" -> {
                    org.bukkit.permissions.Permission permission = (org.bukkit.permissions.Permission) args[0];
                    if (permission.getName().equals(failOnAdd.get())) throw new IllegalStateException("injected add failure");
                    if (permissions.putIfAbsent(permission.getName(), permission) != null) {
                        throw new IllegalArgumentException("already registered");
                    }
                    yield null;
                }
                case "removePermission" -> permissions.remove(((org.bukkit.permissions.Permission) args[0]).getName());
                default -> null;
            });
        }
    }
}
