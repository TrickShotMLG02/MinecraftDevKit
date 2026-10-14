package com.trickshotmlg.devkit.gradle.bukkit.permissions;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.util.Objects;

/** Root extension for MinecraftDevKit Gradle integrations. */
public class MinecraftDevKitExtension {
    private final BukkitPermissionsExtension bukkitPermissions;

    /** Creates the extension and installs the conventional catalog field name.
     * @param objects Gradle object factory
     */
    @Inject
    public MinecraftDevKitExtension(ObjectFactory objects) {
        bukkitPermissions = objects.newInstance(BukkitPermissionsExtension.class);
        bukkitPermissions.getCatalogField().convention("CATALOG");
    }

    /** Returns the Bukkit permission-generation configuration.
     * @return Bukkit permission configuration
     */
    public BukkitPermissionsExtension getBukkitPermissions() {
        return bukkitPermissions;
    }

    /** Configures Bukkit permission generation using a Kotlin/Groovy DSL action.
     * @param action configuration action
     */
    public void bukkitPermissions(Action<? super BukkitPermissionsExtension> action) {
        Objects.requireNonNull(action, "action").execute(bukkitPermissions);
    }
}
