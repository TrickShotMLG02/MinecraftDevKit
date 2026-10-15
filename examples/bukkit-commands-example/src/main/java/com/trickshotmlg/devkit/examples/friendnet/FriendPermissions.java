package com.trickshotmlg.devkit.examples.friendnet;

import com.trickshotmlg.devkit.permissions.DefaultAccess;
import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.permissions.PermissionCatalog;
import com.trickshotmlg.devkit.permissions.PermissionGroup;
import com.trickshotmlg.devkit.permissions.PermissionNamespace;
import com.trickshotmlg.devkit.permissions.PermissionSection;

/** Java-source permission catalog used by the build metadata generator and command definitions. */
public final class FriendPermissions {
    private static final PermissionNamespace NS = PermissionNamespace.create("friendnet");
    private FriendPermissions() { }

    public static final Permission FRIEND_LIST = NS.permission("friends.list");
    public static final Permission FRIEND_LIST_OTHERS = FRIEND_LIST.child("others");
    public static final Permission FRIEND_ADD = NS.permission("friends.add");
    public static final Permission FRIEND_REMOVE = NS.permission("friends.remove");
    public static final Permission REQUEST_LIST = NS.permission("friends.requests.list");
    public static final Permission REQUEST_ACCEPT = NS.permission("friends.requests.accept");
    public static final Permission REQUEST_ACCEPT_ALL = REQUEST_ACCEPT.child("all");
    public static final Permission REQUEST_DENY = NS.permission("friends.requests.deny");
    public static final Permission ADMIN_COMMAND = NS.permission("admin.commands");
    public static final Permission RELOAD = NS.permission("admin.reload");
    public static final Permission PROXY_HANDSHAKE = NS.permission("proxy.handshake");

    public static final PermissionSection FRIENDS_BASIC = NS.section("friends.basic", FRIEND_LIST, FRIEND_ADD, FRIEND_REMOVE);
    public static final PermissionSection REQUESTS_BASIC = NS.section("requests.basic", REQUEST_LIST, REQUEST_ACCEPT, REQUEST_DENY);
    public static final PermissionSection BASIC = NS.section("basic", FRIENDS_BASIC, REQUESTS_BASIC);
    public static final PermissionSection EXTENDED = NS.section("extended", FRIEND_LIST_OTHERS, REQUEST_ACCEPT_ALL);
    public static final PermissionSection STAFF = NS.section("staff", RELOAD);

    public static final PermissionGroup DEFAULT = NS.group("default", group -> group
            .defaultAccess(DefaultAccess.TRUE).grant(BASIC));
    public static final PermissionGroup DEFAULT_EXTENDED = NS.group("default.extended", group -> group
            .inherits(DEFAULT).grant(EXTENDED));
    public static final PermissionGroup MODERATOR = NS.group("moderator", group -> group
            .inherits(DEFAULT_EXTENDED).grant(STAFF).exclude(REQUEST_ACCEPT_ALL));
    public static final PermissionGroup ADMIN_GROUP = NS.group("admin", group -> group
            .defaultAccess(DefaultAccess.OP).grantAllPermissions().exclude(PROXY_HANDSHAKE));
    public static final PermissionCatalog CATALOG = NS.build();
}
