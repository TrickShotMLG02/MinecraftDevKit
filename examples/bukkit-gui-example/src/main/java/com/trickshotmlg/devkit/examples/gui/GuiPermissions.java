package com.trickshotmlg.devkit.examples.gui;

import com.trickshotmlg.devkit.permissions.DefaultAccess;
import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.permissions.PermissionCatalog;
import com.trickshotmlg.devkit.permissions.PermissionNamespace;

/** Permissions exported into plugin.yml and used by the example command. */
public final class GuiPermissions {
    private static final PermissionNamespace NS = PermissionNamespace.create("mdkgui");
    public static final Permission OPEN = NS.permission("open", options -> options.defaultAccess(DefaultAccess.TRUE));
    public static final Permission ADMIN = NS.permission("admin", options -> options.defaultAccess(DefaultAccess.OP));
    public static final PermissionCatalog CATALOG = NS.build();
    private GuiPermissions() { }
}
