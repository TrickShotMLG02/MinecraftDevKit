package com.trickshotmlg.devkit.examples.integration;

import com.trickshotmlg.devkit.permissions.DefaultAccess;
import com.trickshotmlg.devkit.permissions.PermissionCatalog;
import com.trickshotmlg.devkit.permissions.PermissionNamespace;

/** Typed permission catalog. The build plugin exports these declarations to plugin metadata. */
public final class IntegrationPermissions {
    private static final PermissionNamespace NS = PermissionNamespace.create("mdk");
    public static final com.trickshotmlg.devkit.permissions.Permission USE = NS.permission("integration.use",
            options -> options.defaultAccess(DefaultAccess.TRUE));
    public static final com.trickshotmlg.devkit.permissions.Permission SETTINGS = NS.permission("integration.settings",
            options -> options.defaultAccess(DefaultAccess.OP));
    public static final com.trickshotmlg.devkit.permissions.Permission ADMIN = NS.permission("integration.admin",
            options -> options.defaultAccess(DefaultAccess.OP));
    public static final PermissionCatalog CATALOG = NS.build();

    private static final PermissionNamespace RUNTIME_NS = PermissionNamespace.create("mdk_runtime");
    public static final com.trickshotmlg.devkit.permissions.Permission RUNTIME_AUDIT =
            RUNTIME_NS.permission("audit", options -> options.description("Runtime-only acceptance permission."));
    /** Demonstrates registration of a permission not generated into plugin.yml. */
    public static final PermissionCatalog RUNTIME_CATALOG = RUNTIME_NS.build();
    private IntegrationPermissions() { }
}
