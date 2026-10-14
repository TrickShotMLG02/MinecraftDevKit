package com.trickshotmlg.devkit.permissions;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionTest {
    @Test
    void prefixesPermissionNamesAndProvidesStableValueSemanticsWithinNamespace() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        Permission permission = namespace.permission("friends.add");

        assertEquals("friendnet", permission.namespace());
        assertEquals("friends.add", permission.name());
        assertEquals("friendnet.friends.add", permission.node());
        assertEquals("friendnet.friends.add", permission.toString());
        assertEquals(DefaultAccess.FALSE, permission.defaultAccess());
        Set<Permission> indexed = new HashSet<>();
        indexed.add(permission);
        assertTrue(indexed.contains(permission));
    }

    @Test
    void childRegistersASeparatePermissionWithoutGrantInheritance() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        Permission list = namespace.permission("friends.list");
        Permission others = list.child("others");
        PermissionGroup group = namespace.group("default", definition -> definition.grant(list));
        PermissionCatalog catalog = namespace.build();

        assertEquals("friendnet.friends.list.others", others.node());
        assertTrue(catalog.permissions().contains(others));
        assertEquals(Set.of(list), catalog.effectivePermissions(group));
        assertNotEquals(list, others);
    }

    @Test
    void childPathMayContainSeveralValidatedSegments() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        Permission root = namespace.permission("friends");

        assertEquals("friendnet.friends.requests.accept", root.child("requests.accept").node());
    }

    @Test
    void metadataIsCopiedAndOptionsCannotBeChangedAfterDeclaration() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        AtomicReference<PermissionOptions> escapedOptions = new AtomicReference<>();
        Permission permission = namespace.permission("friends.add", options -> {
            escapedOptions.set(options);
            options.description("Allows adding friends").defaultAccess(DefaultAccess.TRUE);
        });

        assertEquals("Allows adding friends", permission.description());
        assertEquals(DefaultAccess.TRUE, permission.defaultAccess());
        assertThrows(IllegalStateException.class,
                () -> escapedOptions.get().defaultAccess(DefaultAccess.OP));
        assertEquals(DefaultAccess.TRUE, permission.defaultAccess());
    }

    @Test
    void namespacesAndPermissionPathsAreStrictlyValidated() {
        assertThrows(IllegalArgumentException.class, () -> PermissionNamespace.create("FriendNet"));
        assertThrows(IllegalArgumentException.class, () -> PermissionNamespace.create("friend.net"));

        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        for (String invalid : new String[]{"", ".friends", "friends.", "friends..add", "Friends.add", "a b", "*"}) {
            assertThrows(IllegalArgumentException.class, () -> namespace.permission(invalid), invalid);
        }
    }

    @Test
    void duplicatePermissionNodesAndCrossNamespaceParentsAreRejected() {
        PermissionNamespace first = PermissionNamespace.create("friendnet");
        Permission existing = first.permission("friends.list");
        assertThrows(IllegalArgumentException.class, () -> first.permission("friends.list"));

        PermissionNamespace second = PermissionNamespace.create("economy");
        assertEquals("friendnet.friends.list.others", existing.child("others").node());
        assertThrows(IllegalArgumentException.class, () -> second.section("external", existing));
    }

    @Test
    void permissionCannotBeAddedAfterBuild() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        namespace.permission("friends.list");
        PermissionCatalog catalog = namespace.build();

        assertThrows(IllegalStateException.class, () -> namespace.permission("friends.add"));
        assertEquals(catalog, namespace.build());
    }

    @Test
    void equalNodesInDifferentNamespacesRemainIsolated() {
        Permission left = PermissionNamespace.create("friendnet").permission("admin");
        Permission right = PermissionNamespace.create("friendnet").permission("admin");

        assertFalse(left.equals(right));
        assertEquals(2, Set.of(left, right).size());
    }
}
