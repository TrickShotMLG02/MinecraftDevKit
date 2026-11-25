# devkit-bukkit-permissions

Registers a platform-independent `PermissionCatalog` with Bukkit's runtime `PluginManager` on Spigot and Paper.

```java
private BukkitPermissionRegistration permissions;

@Override public void onEnable() {
    permissions = BukkitPermissions.register(this, MyPermissions.CATALOG);
}

@Override public void onDisable() {
    if (permissions != null) permissions.close();
}
```

Registration must run on the Bukkit primary thread. The handle also listens for its owning plugin's disable event and closes idempotently. It removes only the exact `Permission` instances it added, and leaves matching pre-existing definitions (including plugin.yml metadata) and foreign registrations untouched. Conflicting pre-existing metadata raises `BukkitPermissionRegistrationException` with the node name. `diagnostics()` reports newly registered and reused nodes.

Ordinary permission definitions map directly to Bukkit permission defaults. Catalog groups are registered as separate group nodes with positive child assignments from the catalog's already-resolved effective grants. Exclusions omit a child; no negative child assignment is created. This preserves the catalog's definition-time grant set without turning exclusions into global denials. Bukkit attachments and other plugins can still grant nodes independently, and closing registrations does not revoke such assignments. This adapter registers definitions; it is not a player-role or permission-management system. Folia is not supported by this traditional primary-thread adapter.
