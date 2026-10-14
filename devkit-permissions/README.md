# `devkit-permissions`

`devkit-permissions` is a Java 17, platform-independent model for declaring permission nodes, organizing them into reusable sections, and describing groups that select permissions. Java source is the source of truth, so ordinary permission references are typed constants that work with IDE completion and refactoring.

The module validates definitions and resolves immutable catalogs. It does **not** assign permissions to players, evaluate users or operators, implement wildcard checks, connect to LuckPerms, register Bukkit permissions, or generate `plugin.yml`.

## Quick start

```java
import com.trickshotmlg.devkit.permissions.DefaultAccess;
import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.permissions.PermissionCatalog;
import com.trickshotmlg.devkit.permissions.PermissionGroup;
import com.trickshotmlg.devkit.permissions.PermissionNamespace;
import com.trickshotmlg.devkit.permissions.PermissionSection;

public final class ShopPermissions {
    private static final PermissionNamespace NS = PermissionNamespace.create("example_shop");

    public static final Permission SHOP_VIEW = NS.permission("shop.view");
    public static final Permission SHOP_VIEW_OTHERS = SHOP_VIEW.child("others");
    public static final Permission SHOP_BUY = NS.permission("shop.buy", options -> options
            .description("Allows purchasing an item")
            .defaultAccess(DefaultAccess.TRUE));

    public static final PermissionSection BASIC = NS.section("basic", SHOP_VIEW, SHOP_BUY);
    public static final PermissionGroup MEMBER = NS.group("member", group -> group
            .defaultAccess(DefaultAccess.TRUE)
            .grant(BASIC));
    public static final PermissionGroup MODERATOR = NS.group("moderator", group -> group
            .inherits(MEMBER)
            .grant(SHOP_VIEW_OTHERS));

    public static final PermissionCatalog CATALOG = NS.build();

    private ShopPermissions() { }
}
```

The catalog provides immutable, resolved selections:

```java
var basicPermissions = ShopPermissions.CATALOG.permissionsOf(ShopPermissions.BASIC);
var moderatorPermissions = ShopPermissions.CATALOG.effectivePermissions(ShopPermissions.MODERATOR);
```

Each namespace is local to the class or component declaring it; there is no global registry or global prefix.

## Complete FriendNet example

This example exercises permissions, child names, nested sections, group inheritance, exclusions, and all-permissions selection. The executable version is covered by `FriendPermissionsExampleTest`.

```java
import com.trickshotmlg.devkit.permissions.DefaultAccess;
import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.permissions.PermissionCatalog;
import com.trickshotmlg.devkit.permissions.PermissionGroup;
import com.trickshotmlg.devkit.permissions.PermissionNamespace;
import com.trickshotmlg.devkit.permissions.PermissionSection;

public final class FriendPermissions {
    private static final PermissionNamespace NS = PermissionNamespace.create("friendnet");
    private FriendPermissions() { }

    // Permissions
    public static final Permission FRIEND_LIST = NS.permission("friends.list");
    public static final Permission FRIEND_LIST_OTHERS = FRIEND_LIST.child("others");
    public static final Permission FRIEND_ADD = NS.permission("friends.add");
    public static final Permission FRIEND_REMOVE = NS.permission("friends.remove");
    public static final Permission FRIEND_BLOCK = NS.permission("friends.block");
    public static final Permission REQUEST_LIST = NS.permission("friends.requests.list");
    public static final Permission REQUEST_ACCEPT = NS.permission("friends.requests.accept");
    public static final Permission REQUEST_ACCEPT_ALL = REQUEST_ACCEPT.child("all");
    public static final Permission RELOAD = NS.permission("reload");
    public static final Permission PROXY_HANDSHAKE = NS.permission("proxy.handshake");

    // Sections (organizational selections, not nodes)
    public static final PermissionSection FRIENDS_BASIC = NS.section("friends.basic",
            FRIEND_LIST, FRIEND_ADD, FRIEND_REMOVE);
    public static final PermissionSection REQUESTS_BASIC = NS.section("requests.basic",
            REQUEST_LIST, REQUEST_ACCEPT);
    public static final PermissionSection BASIC = NS.section("basic", FRIENDS_BASIC, REQUESTS_BASIC);
    public static final PermissionSection EXTENDED = NS.section("extended",
            FRIEND_LIST_OTHERS, REQUEST_ACCEPT_ALL);
    public static final PermissionSection STAFF = NS.section("staff", FRIEND_BLOCK, RELOAD);

    // Groups (real nodes with metadata and resolved grants)
    public static final PermissionGroup DEFAULT = NS.group("default", group -> group
            .defaultAccess(DefaultAccess.TRUE).grant(BASIC));
    public static final PermissionGroup DEFAULT_EXTENDED = NS.group("default.extended", group -> group
            .inherits(DEFAULT).grant(EXTENDED));
    public static final PermissionGroup MODERATOR = NS.group("moderator", group -> group
            .inherits(DEFAULT_EXTENDED).grant(STAFF).exclude(REQUEST_ACCEPT_ALL));
    public static final PermissionGroup ADMIN = NS.group("admin", group -> group
            .defaultAccess(DefaultAccess.OP).grantAllPermissions().exclude(PROXY_HANDSHAKE));

    public static final PermissionCatalog CATALOG = NS.build();
}
```

## Design principles and semantics

### Permission names are not authorization relationships

`FRIEND_LIST.child("others")` declares the separate node `friendnet.friends.list.others`. The parent does not grant the child. The child can only enter a group's effective selection through a direct grant, a granted section, inheritance, or `grantAllPermissions()`.

Identifiers use lowercase ASCII letters, digits, underscores, and hyphens. A dot separates non-empty path segments. Namespace names are a single segment; permission and group names may contain multiple segments. Invalid names are rejected instead of silently normalized.

### Sections organize; groups represent nodes

A section such as `friends.basic` is organizational metadata. It has no node and cannot be looked up as a permission. Sections can contain permissions and previously declared sections; resolution produces an ordered, deduplicated set.

A group such as `friendnet.moderator` is an actual group permission node. Groups are not section members. A group grants permissions or sections and may inherit other groups explicitly.

### Inheritance and exclusions

Group inheritance is transitive and combines the parent's **effective permission grants**. It does not grant the parent's group node. Names like `default` and `default.extended` have no special relationship until `.inherits(DEFAULT)` is declared.

Resolution follows this order: inherited effective sets in declaration order, all registered ordinary permissions if selected, then direct grants. Exclusions are expanded and removed after the union. An exclusion applies to this group's result, including grants inherited from parents. It is not an explicit deny and cannot cancel the same permission granted independently by a platform, another group, or another source.

`grantAllPermissions()` is resolved at `build()`, so it includes ordinary permission declarations added later in the same namespace. It does not include group nodes.

### Default access is metadata

`DefaultAccess` provides `TRUE`, `FALSE`, `OP`, and `NOT_OP` values for adapters that support these defaults. Ordinary permissions and groups default to `FALSE`. These values do not change catalog resolution and the core does not inspect player/operator state.

### Lifecycle and validation

Create a namespace, register permissions, sections, and groups, then call `build()`. The catalog and all definitions are immutable; repeated `build()` calls return the same catalog, and further registration throws `IllegalStateException`.

The namespace rejects duplicate nodes/names, permission/group node collisions, invalid identifiers, unsupported or foreign members, unknown group references, and registration after build. Definitions can only refer to already registered sections and groups. That construction rule makes cycles unconstructible through the public API; the resolver still detects cycles defensively.

## Public API reference

- `PermissionNamespace.create(name)` creates an isolated declaration scope. Use `permission`, `section`, and `group` to declare definitions and `build()` to freeze and resolve them.
- `Permission` is an immutable ordinary permission node. `node()` is fully qualified; `name()` is relative to its namespace; `namespace()`, `description()`, and `defaultAccess()` expose metadata. `child(path)` registers another ordinary permission and does not establish inheritance.
- `PermissionOptions` configures description and default access during a declaration callback. Its values are copied into the immutable permission.
- `PermissionMember` is the common typed selection type implemented by `Permission` and `PermissionSection`. Groups are deliberately excluded.
- `PermissionSection` is an immutable named selection. `members()` exposes its direct immutable members; `catalog.permissionsOf(section)` returns its recursively resolved members.
- `PermissionGroup` is an immutable group node with direct grants, exclusions, inheritance references, description, and default access metadata. Its builder is usable only during the `group` callback.
- `DefaultAccess` contains the four platform-neutral default values.
- `PermissionCatalog` exposes immutable ordered sets through `permissions()`, `sections()`, and `groups()`, resolved sets through `permissionsOf` and `effectivePermissions`, and optional node/name lookups through `findPermission`, `findGroup`, and `findSection`.

## Future platform integration

Platform adapters can consume the catalog without adding platform APIs to this module. For example, a future Bukkit exporter can use each group's `node()`, `description()`, and `defaultAccess()` along with `effectivePermissions(group)` to flatten positive child grants. Flattening the resolved set preserves exclusions and avoids reintroducing a parent's excluded permissions through native group inheritance. No Bukkit registration or YAML generation is implemented here.

The same Java definitions and catalog can support future Paper, Velocity, Fabric, Forge, and NeoForge adapters, each of which can translate the model into its own native permission behavior.
