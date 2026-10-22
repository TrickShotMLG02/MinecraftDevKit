package com.trickshotmlg.devkit.examples.storage;

import com.trickshotmlg.devkit.permissions.DefaultAccess;
import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.permissions.PermissionCatalog;
import com.trickshotmlg.devkit.permissions.PermissionNamespace;

/** Permission declarations are the source for generated plugin.yml metadata. */
public final class StoragePermissions {
    private static final PermissionNamespace NS = PermissionNamespace.create("mdk");
    public static final Permission BALANCE = NS.permission("storage.balance", options -> options.defaultAccess(DefaultAccess.TRUE));
    public static final Permission ADMIN = NS.permission("storage.admin", options -> options.defaultAccess(DefaultAccess.OP));
    public static final PermissionCatalog CATALOG = NS.build();
    private StoragePermissions() { }
}
