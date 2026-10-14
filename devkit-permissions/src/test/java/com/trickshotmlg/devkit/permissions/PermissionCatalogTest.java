package com.trickshotmlg.devkit.permissions;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionCatalogTest {
    @Test
    void buildResolvesAndCachesImmutableCatalogResults() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        Permission first = namespace.permission("first");
        Permission second = namespace.permission("second");
        PermissionSection section = namespace.section("both", first, second);
        PermissionGroup group = namespace.group("member", definition -> definition.grant(section));

        PermissionCatalog catalog = namespace.build();

        assertSame(catalog, namespace.build());
        assertEquals(List.of(first, second), new ArrayList<>(catalog.permissions()));
        assertEquals(List.of(section), new ArrayList<>(catalog.sections()));
        assertEquals(List.of(group), new ArrayList<>(catalog.groups()));
        assertSame(catalog.permissionsOf(section), catalog.permissionsOf(section));
        assertSame(catalog.effectivePermissions(group), catalog.effectivePermissions(group));
    }

    @Test
    void lookupsUseActualNodesAndReturnOptionalValues() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        Permission permission = namespace.permission("friends.read");
        PermissionSection section = namespace.section("readers", permission);
        PermissionGroup group = namespace.group("reader", definition -> definition.grant(section));
        PermissionCatalog catalog = namespace.build();

        assertEquals(permission, catalog.findPermission("friendnet.friends.read").orElseThrow());
        assertEquals(group, catalog.findGroup("friendnet.reader").orElseThrow());
        assertEquals(section, catalog.findSection("readers").orElseThrow());
        assertTrue(catalog.findPermission("friendnet.reader").isEmpty());
        assertFalse(catalog.findGroup("friendnet.friends.read").isPresent());
    }

    @Test
    void immutableCatalogRejectsDefinitionsFromOtherNamespaces() {
        PermissionNamespace first = PermissionNamespace.create("friendnet");
        Permission firstPermission = first.permission("read");
        PermissionGroup firstGroup = first.group("reader", group -> group.grant(firstPermission));
        PermissionCatalog catalog = first.build();

        PermissionNamespace second = PermissionNamespace.create("friendnet");
        Permission otherPermission = second.permission("read");
        PermissionSection otherSection = second.section("other", otherPermission);
        PermissionGroup otherGroup = second.group("reader", group -> group.grant(otherPermission));
        second.build();

        assertThrows(IllegalArgumentException.class, () -> catalog.permissionsOf(otherSection));
        assertThrows(IllegalArgumentException.class, () -> catalog.effectivePermissions(otherGroup));
        assertEquals(Set.of(firstPermission), catalog.effectivePermissions(firstGroup));
    }

    @Test
    void independentNamespacesAndCatalogsDoNotShareDeclarations() {
        PermissionNamespace friends = PermissionNamespace.create("friendnet");
        Permission friend = friends.permission("view");
        PermissionCatalog friendCatalog = friends.build();
        PermissionNamespace economy = PermissionNamespace.create("economy");
        Permission balance = economy.permission("view");
        PermissionCatalog economyCatalog = economy.build();

        assertEquals(Set.of(friend), friendCatalog.permissions());
        assertEquals(Set.of(balance), economyCatalog.permissions());
        assertTrue(friendCatalog.findPermission("economy.view").isEmpty());
    }

    @Test
    void registrationAfterBuildFailsForEveryDefinitionKind() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        Permission permission = namespace.permission("read");
        namespace.section("readers", permission);
        namespace.group("reader", group -> group.grant(permission));
        namespace.build();

        assertThrows(IllegalStateException.class, () -> namespace.permission("write"));
        assertThrows(IllegalStateException.class, () -> namespace.section("writers"));
        assertThrows(IllegalStateException.class, () -> namespace.group("writer", group -> { }));
    }

    @Test
    void unknownOrNullCatalogQueriesFailClearly() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        PermissionCatalog catalog = namespace.build();

        assertThrows(NullPointerException.class, () -> catalog.findPermission(null));
        assertThrows(NullPointerException.class, () -> catalog.findGroup(null));
        assertThrows(NullPointerException.class, () -> catalog.permissionsOf(null));
    }
}
