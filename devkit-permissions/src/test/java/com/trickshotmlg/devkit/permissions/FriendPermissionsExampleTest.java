package com.trickshotmlg.devkit.permissions;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compiles and verifies the intended Java-source-first permission declaration style. */
class FriendPermissionsExampleTest {
    @Test
    void completeFriendNetExampleResolvesExpectedCatalog() {
        PermissionCatalog catalog = FriendPermissions.CATALOG;

        assertEquals(Set.of(FriendPermissions.FRIEND_LIST, FriendPermissions.FRIEND_ADD,
                        FriendPermissions.FRIEND_REMOVE, FriendPermissions.REQUEST_LIST,
                        FriendPermissions.REQUEST_ACCEPT),
                catalog.effectivePermissions(FriendPermissions.DEFAULT));

        assertEquals(Set.of(FriendPermissions.FRIEND_LIST, FriendPermissions.FRIEND_ADD,
                        FriendPermissions.FRIEND_REMOVE, FriendPermissions.REQUEST_LIST,
                        FriendPermissions.REQUEST_ACCEPT, FriendPermissions.FRIEND_LIST_OTHERS,
                        FriendPermissions.REQUEST_ACCEPT_ALL),
                catalog.effectivePermissions(FriendPermissions.DEFAULT_EXTENDED));

        Set<Permission> moderatorPermissions = catalog.effectivePermissions(FriendPermissions.MODERATOR);
        assertEquals(Set.of(FriendPermissions.FRIEND_LIST,
                FriendPermissions.FRIEND_ADD, FriendPermissions.FRIEND_REMOVE,
                FriendPermissions.REQUEST_LIST, FriendPermissions.REQUEST_ACCEPT,
                FriendPermissions.FRIEND_LIST_OTHERS, FriendPermissions.FRIEND_BLOCK,
                FriendPermissions.RELOAD), moderatorPermissions);
        assertFalse(moderatorPermissions.contains(FriendPermissions.REQUEST_ACCEPT_ALL));

        Set<Permission> adminPermissions = catalog.effectivePermissions(FriendPermissions.ADMIN);
        assertEquals(catalog.permissions().size() - 1, adminPermissions.size());
        assertFalse(adminPermissions.contains(FriendPermissions.PROXY_HANDSHAKE));
        assertTrue(adminPermissions.contains(FriendPermissions.REQUEST_ACCEPT_ALL));

        assertFalse(catalog.effectivePermissions(FriendPermissions.DEFAULT)
                .contains(FriendPermissions.FRIEND_LIST_OTHERS));
        assertTrue(catalog.effectivePermissions(FriendPermissions.DEFAULT_EXTENDED)
                .contains(FriendPermissions.FRIEND_LIST_OTHERS));

        assertTrue(catalog.findPermission("friendnet.basic").isEmpty());
        assertTrue(catalog.findPermission("friendnet.default").isEmpty());
        assertTrue(catalog.findGroup("friendnet.default").isPresent());
        assertFalse(catalog.effectivePermissions(FriendPermissions.DEFAULT_EXTENDED)
                .contains(FriendPermissions.ADMIN));
    }

}
