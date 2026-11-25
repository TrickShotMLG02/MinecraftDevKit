package com.trickshotmlg.devkit.gradle.bukkit.permissions;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.util.Objects;
import org.gradle.api.provider.Property;
import com.trickshotmlg.devkit.gradle.paper.PaperPluginMetadataExtension;
import com.trickshotmlg.devkit.gradle.paper.PluginDescriptorPlatform;

/** Root extension for MinecraftDevKit Gradle integrations. */
public class MinecraftDevKitExtension {
    private final BukkitPermissionsExtension bukkitPermissions;
    private final PaperPluginMetadataExtension paperPlugin;
    private final Property<PluginDescriptorPlatform> platform;

    /** Creates the extension and installs the conventional catalog field name.
     * @param objects Gradle object factory
     */
    @Inject
    public MinecraftDevKitExtension(ObjectFactory objects) {
        bukkitPermissions = objects.newInstance(BukkitPermissionsExtension.class);
        bukkitPermissions.getCatalogField().convention("CATALOG");
        paperPlugin = objects.newInstance(PaperPluginMetadataExtension.class);
        platform = objects.property(PluginDescriptorPlatform.class);
        platform.convention(PluginDescriptorPlatform.BUKKIT);
    }

    /** Returns the Bukkit permission-generation configuration.
     * @return Bukkit permission configuration
     */
    public BukkitPermissionsExtension getBukkitPermissions() {
        return bukkitPermissions;
    }

    /** @return descriptor target; defaults to a Bukkit-compatible {@code plugin.yml} */
    public Property<PluginDescriptorPlatform> getPlatform() { return platform; }

    /** @return native Paper descriptor configuration */
    public PaperPluginMetadataExtension getPaperPlugin() { return paperPlugin; }

    /** Configures Bukkit permission generation using a Kotlin/Groovy DSL action.
     * @param action configuration action
     */
    public void bukkitPermissions(Action<? super BukkitPermissionsExtension> action) {
        Objects.requireNonNull(action, "action").execute(bukkitPermissions);
    }

    /** Configures a generated native Paper {@code paper-plugin.yml}. */
    public void paperPlugin(Action<? super PaperPluginMetadataExtension> action) {
        Objects.requireNonNull(action, "action").execute(paperPlugin);
    }
}
