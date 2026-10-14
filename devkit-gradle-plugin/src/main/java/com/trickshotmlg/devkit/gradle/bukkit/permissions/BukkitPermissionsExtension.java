package com.trickshotmlg.devkit.gradle.bukkit.permissions;

import org.gradle.api.provider.Property;

/** Configuration for generating Bukkit permission metadata from a Java catalog. */
public abstract class BukkitPermissionsExtension {
    /**
     * Fully qualified name of the class that declares the static catalog field.
     * @return catalog class property
     */
    public abstract Property<String> getCatalogClass();

    /**
     * Name of a public static field containing a {@code PermissionCatalog}; defaults to
     * {@code CATALOG}.
     * @return catalog field property
     */
    public abstract Property<String> getCatalogField();
}
