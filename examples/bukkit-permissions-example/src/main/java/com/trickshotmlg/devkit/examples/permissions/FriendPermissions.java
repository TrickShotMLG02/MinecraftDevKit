package com.trickshotmlg.devkit.examples.permissions;

import com.trickshotmlg.devkit.permissions.DefaultAccess;
import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.permissions.PermissionCatalog;
import com.trickshotmlg.devkit.permissions.PermissionGroup;
import com.trickshotmlg.devkit.permissions.PermissionNamespace;
import com.trickshotmlg.devkit.permissions.PermissionSection;

/** Java source of truth for the example plugin's permission metadata. */
public final class FriendPermissions {
    private static final PermissionNamespace NS = PermissionNamespace.create("mdkperms");

    private FriendPermissions() { }

    public static final Permission FRIEND_LIST = NS.permission("friends.list", options -> options
            .description("Allows viewing the player's friend list."));
    public static final Permission FRIEND_LIST_OTHERS = FRIEND_LIST.child("others");
    public static final Permission FRIEND_ADD = NS.permission("friends.add", options -> options
            .description("Allows sending friend requests."));
    public static final Permission FRIEND_REMOVE = NS.permission("friends.remove", options -> options
            .description("Allows removing friends."));
    public static final Permission FRIEND_BLOCK = NS.permission("friends.block", options -> options
            .description("Allows blocking players."));

    public static final Permission REQUEST_LIST = NS.permission("friends.requests.list", options -> options
            .description("Allows viewing incoming friend requests."));
    public static final Permission REQUEST_ACCEPT = NS.permission("friends.requests.accept", options -> options
            .description("Allows accepting a friend request."));
    public static final Permission REQUEST_ACCEPT_ALL = REQUEST_ACCEPT.child("all");

    public static final Permission RELOAD = NS.permission("reload", options -> options
            .description("Allows reloading FriendNet."));
    public static final Permission PROXY_HANDSHAKE = NS.permission("proxy.handshake", options -> options
            .description("Allows the trusted proxy handshake."));

    public static final PermissionSection FRIENDS_BASIC = NS.section("friends.basic",
            FRIEND_LIST, FRIEND_ADD, FRIEND_REMOVE);
    public static final PermissionSection REQUESTS_BASIC = NS.section("requests.basic",
            REQUEST_LIST, REQUEST_ACCEPT);
    public static final PermissionSection BASIC = NS.section("basic", FRIENDS_BASIC, REQUESTS_BASIC);
    public static final PermissionSection EXTENDED = NS.section("extended",
            FRIEND_LIST_OTHERS, REQUEST_ACCEPT_ALL);
    public static final PermissionSection STAFF = NS.section("staff", FRIEND_BLOCK, RELOAD);

    public static final PermissionGroup DEFAULT = NS.group("default", group -> group
            .description("Basic FriendNet access for every player.")
            .defaultAccess(DefaultAccess.TRUE)
            .grant(BASIC));
    public static final PermissionGroup DEFAULT_EXTENDED = NS.group("default.extended", group -> group
            .description("Basic and extended FriendNet access.")
            .inherits(DEFAULT)
            .grant(EXTENDED));
    public static final PermissionGroup MODERATOR = NS.group("moderator", group -> group
            .description("Moderator FriendNet access, without accepting all requests.")
            .inherits(DEFAULT_EXTENDED)
            .grant(STAFF)
            .exclude(REQUEST_ACCEPT_ALL));
    public static final PermissionGroup ADMIN = NS.group("admin", group -> group
            .description("All FriendNet permissions except the trusted proxy handshake.")
            .defaultAccess(DefaultAccess.OP)
            .grantAllPermissions()
            .exclude(PROXY_HANDSHAKE));

    public static final PermissionCatalog CATALOG = NS.build();
}
