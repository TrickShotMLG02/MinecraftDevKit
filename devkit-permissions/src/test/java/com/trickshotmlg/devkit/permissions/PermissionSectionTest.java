package com.trickshotmlg.devkit.permissions;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionSectionTest {
    @Test
    void nestedSectionsResolveUnionWithoutDuplicatesAndPreserveOrder() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        Permission first = namespace.permission("first");
        Permission second = namespace.permission("second");
        Permission third = namespace.permission("third");
        PermissionSection left = namespace.section("left", first, second);
        PermissionSection right = namespace.section("right", second, third);
        PermissionSection root = namespace.section("root", left, right, first);

        List<Permission> resolved = new ArrayList<>(namespace.build().permissionsOf(root));

        assertEquals(List.of(first, second, third), resolved);
        assertEquals(List.of(left, right, first), root.members());
    }

    @Test
    void sectionNameIsOrganizationalAndDoesNotCreateANode() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        Permission item = namespace.permission("friends.list");
        PermissionSection section = namespace.section("friends.basic", item);
        PermissionCatalog catalog = namespace.build();

        assertTrue(catalog.findPermission("friendnet.friends.basic").isEmpty());
        assertEquals(Set.of(item), catalog.permissionsOf(section));
        assertEquals("friends.basic", section.name());
    }

    @Test
    void emptyAndDuplicateMembershipsAreHandledSafely() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        Permission permission = namespace.permission("read");
        PermissionSection empty = namespace.section("empty");
        PermissionSection repeated = namespace.section("repeated", permission, permission);
        PermissionCatalog catalog = namespace.build();

        assertTrue(catalog.permissionsOf(empty).isEmpty());
        assertEquals(Set.of(permission), catalog.permissionsOf(repeated));
    }

    @Test
    void registeredSectionsAndMembersAreImmutable() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        Permission permission = namespace.permission("read");
        PermissionSection section = namespace.section("readers", permission);
        PermissionCatalog catalog = namespace.build();

        assertThrows(UnsupportedOperationException.class, () -> section.members().add(permission));
        assertThrows(UnsupportedOperationException.class, () -> catalog.permissionsOf(section).clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.sections().clear());
    }

    @Test
    void foreignAndUnsupportedMembersAreRejected() {
        PermissionNamespace owner = PermissionNamespace.create("friendnet");
        PermissionNamespace foreign = PermissionNamespace.create("economy");
        Permission elsewhere = foreign.permission("balance.view");

        assertThrows(IllegalArgumentException.class, () -> owner.section("foreign", elsewhere));
        assertThrows(IllegalArgumentException.class, () -> owner.section("unsupported", new PermissionMember() { }));
    }

    @Test
    void sectionNamesAreUniqueAndRegistrationStopsAfterBuild() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        namespace.section("basic");
        assertThrows(IllegalArgumentException.class, () -> namespace.section("basic"));
        namespace.build();

        assertThrows(IllegalStateException.class, () -> namespace.section("later"));
    }
}
