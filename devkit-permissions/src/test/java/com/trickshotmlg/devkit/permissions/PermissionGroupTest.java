package com.trickshotmlg.devkit.permissions;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionGroupTest {
    @Test
    void resolvesDirectAndSectionGrantsAndTransitiveInheritance() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        Permission view = namespace.permission("friends.view");
        Permission add = namespace.permission("friends.add");
        Permission remove = namespace.permission("friends.remove");
        PermissionSection basic = namespace.section("basic", view, add);
        PermissionGroup defaults = namespace.group("default", group -> group.grant(basic));
        PermissionGroup extended = namespace.group("default.extended", group -> group
                .inherits(defaults).grant(remove));
        PermissionGroup moderator = namespace.group("moderator", group -> group.inherits(extended));
        PermissionCatalog catalog = namespace.build();

        assertEquals(Set.of(view, add), catalog.effectivePermissions(defaults));
        assertEquals(Set.of(view, add, remove), catalog.effectivePermissions(extended));
        assertEquals(Set.of(view, add, remove), catalog.effectivePermissions(moderator));
        assertFalse(catalog.effectivePermissions(extended).contains(defaults));
    }

    @Test
    void multipleParentsAreUnionedAndGroupNamesHaveNoImplicitInheritance() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        Permission one = namespace.permission("one");
        Permission two = namespace.permission("two");
        PermissionGroup first = namespace.group("default", group -> group.grant(one));
        PermissionGroup second = namespace.group("staff", group -> group.grant(two));
        PermissionGroup combined = namespace.group("combined", group -> group.inherits(first, second));
        PermissionCatalog catalog = namespace.build();

        assertEquals(Set.of(one, two), catalog.effectivePermissions(combined));
        assertFalse(catalog.effectivePermissions(combined).contains(first));
        assertFalse(catalog.effectivePermissions(combined).contains(second));
    }

    @Test
    void exclusionsRemoveDirectAndInheritedGrantsWithoutBeingDenials() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        Permission one = namespace.permission("one");
        Permission two = namespace.permission("two");
        Permission three = namespace.permission("three");
        PermissionSection selection = namespace.section("selection", two, three);
        PermissionGroup parent = namespace.group("parent", group -> group.grant(one, selection));
        PermissionGroup child = namespace.group("child", group -> group
                .inherits(parent).grant(three).exclude(one, selection));
        PermissionCatalog catalog = namespace.build();

        assertEquals(Set.of(), catalog.effectivePermissions(child));
        assertEquals(Set.of(one, two, three), catalog.effectivePermissions(parent));
    }

    @Test
    void exclusionsDoNotPropagateAsNegativeRulesToAnotherGroup() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        Permission item = namespace.permission("item");
        PermissionGroup excluded = namespace.group("excluded", group -> group.exclude(item));
        PermissionGroup other = namespace.group("other", group -> group.grant(item));
        PermissionCatalog catalog = namespace.build();

        assertTrue(catalog.effectivePermissions(excluded).isEmpty());
        assertEquals(Set.of(item), catalog.effectivePermissions(other));
    }

    @Test
    void allPermissionsResolvesAtBuildAndDoesNotIncludeGroupNodes() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        Permission first = namespace.permission("first");
        PermissionGroup admin = namespace.group("admin", group -> group.grantAllPermissions());
        Permission late = namespace.permission("late");
        namespace.group("later-group", group -> group.grant(first));
        PermissionCatalog catalog = namespace.build();

        assertEquals(Set.of(first, late), catalog.effectivePermissions(admin));
        assertFalse(catalog.findPermission("friendnet.admin").isPresent());
        assertTrue(catalog.findGroup("friendnet.admin").isPresent());
    }

    @Test
    void groupOptionsAreImmutableAndDefaultAccessIsMetadataOnly() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        AtomicReference<PermissionGroup.Builder> escaped = new AtomicReference<>();
        PermissionGroup group = namespace.group("admin", definition -> {
            escaped.set(definition);
            definition.description("Administrator group").defaultAccess(DefaultAccess.OP);
        });
        namespace.permission("permission");
        PermissionCatalog catalog = namespace.build();

        assertEquals("friendnet.admin", group.node());
        assertEquals("Administrator group", group.description());
        assertEquals(DefaultAccess.OP, group.defaultAccess());
        assertTrue(catalog.effectivePermissions(group).isEmpty());
        assertThrows(IllegalStateException.class, () -> escaped.get().grantAllPermissions());
    }

    @Test
    void invalidInheritanceReferencesAndNodeCollisionsAreRejectedAtomically() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        PermissionGroup parent = namespace.group("parent", group -> { });
        PermissionNamespace foreign = PermissionNamespace.create("economy");
        PermissionGroup foreignGroup = foreign.group("foreign", group -> { });

        assertThrows(IllegalArgumentException.class,
                () -> namespace.group("bad", group -> group.inherits(foreignGroup)));
        assertThrows(IllegalArgumentException.class,
                () -> namespace.group("parent", group -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> namespace.permission("parent"));
        assertThrows(IllegalArgumentException.class,
                () -> namespace.group("bad", group -> group.grant(foreign.permission("outside"))));
        assertEquals("friendnet.parent", parent.node());
        assertTrue(namespace.build().groups().contains(parent));
    }

    @Test
    void callbackFailureDoesNotLeavePartialGroupOrMutableBuilder() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        AtomicReference<PermissionGroup.Builder> escaped = new AtomicReference<>();

        assertThrows(IllegalStateException.class, () -> namespace.group("broken", group -> {
            escaped.set(group);
            group.grantAllPermissions();
            throw new IllegalStateException("definition failed");
        }));
        assertThrows(IllegalStateException.class, () -> escaped.get().grantAllPermissions());

        PermissionGroup retried = namespace.group("broken", group -> { });
        assertFalse(retried.grantsAllPermissions());
    }

    @Test
    void groupCallbackCannotReentrantlyMutateItsNamespace() {
        PermissionNamespace namespace = PermissionNamespace.create("friendnet");

        assertThrows(IllegalStateException.class, () -> namespace.group("broken", group -> {
            group.grantAllPermissions();
            namespace.permission("partial");
        }));
        assertTrue(namespace.group("broken", group -> { }).grants().isEmpty());
        assertTrue(namespace.build().permissions().isEmpty());
    }
}
