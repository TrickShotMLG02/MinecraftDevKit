package com.trickshotmlg.devkit.gradle.paper;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Typed configuration for a native Paper {@code paper-plugin.yml}. */
public abstract class PaperPluginMetadataExtension {
    private final ObjectFactory objects;
    private final List<PaperPluginDependency> bootstrapDependencies = new ArrayList<>();
    private final List<PaperPluginDependency> serverDependencies = new ArrayList<>();

    @Inject
    public PaperPluginMetadataExtension(ObjectFactory objects) {
        this.objects = objects;
        getAuthors().convention(List.of());
        getBootstrapDependencySpecs().convention(List.of());
        getServerDependencySpecs().convention(List.of());
    }

    /** @return Paper plugin name */
    public abstract Property<String> getName();
    /** @return plugin version */
    public abstract Property<String> getVersion();
    /** @return fully qualified JavaPlugin class */
    public abstract Property<String> getMainClass();
    /** @return optional descriptor description */
    public abstract Property<String> getDescription();
    /** @return optional Paper API version string */
    public abstract Property<String> getApiVersion();
    /** @return optional PluginBootstrap class */
    public abstract Property<String> getBootstrapper();
    /** @return optional PluginLoader class */
    public abstract Property<String> getLoader();
    /** @return descriptor authors in declared order */
    public abstract ListProperty<String> getAuthors();
    /** @return task-ready bootstrap dependencies */
    public abstract ListProperty<PaperPluginDependencySpec> getBootstrapDependencySpecs();
    /** @return task-ready server dependencies */
    public abstract ListProperty<PaperPluginDependencySpec> getServerDependencySpecs();

    /** Adds a dependency needed by bootstrap code. */
    public void bootstrapDependency(String pluginName, Action<? super PaperPluginDependency> action) {
        add(bootstrapDependencies, getBootstrapDependencySpecs(), pluginName, PaperDependencyPhase.BOOTSTRAP, action);
    }

    /** Adds a dependency needed during normal plugin runtime. */
    public void serverDependency(String pluginName, Action<? super PaperPluginDependency> action) {
        add(serverDependencies, getServerDependencySpecs(), pluginName, PaperDependencyPhase.SERVER, action);
    }

    private void add(List<PaperPluginDependency> definitions,
                     ListProperty<PaperPluginDependencySpec> target, String name,
                     PaperDependencyPhase phase, Action<? super PaperPluginDependency> action) {
        Objects.requireNonNull(name, "pluginName");
        Objects.requireNonNull(action, "action");
        PaperPluginDependency dependency = objects.newInstance(PaperPluginDependency.class, name);
        action.execute(dependency);
        definitions.add(dependency);
        target.add(dependency.snapshot(phase));
    }
}
