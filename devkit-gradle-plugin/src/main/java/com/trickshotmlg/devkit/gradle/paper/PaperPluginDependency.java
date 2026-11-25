package com.trickshotmlg.devkit.gradle.paper;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/** DSL configuration for a Paper plugin dependency. */
public abstract class PaperPluginDependency {
    private final String name;

    @Inject
    public PaperPluginDependency(String name, ObjectFactory objects) {
        this.name = name;
        getLoad().convention(PaperDependencyLoadOrder.OMIT);
        getRequired().convention(true);
        // Paper defaults this to true; MinecraftDevKit keeps plugin classpaths isolated unless
        // the author explicitly opts in to accessing another plugin's classes.
        getJoinClasspath().convention(false);
    }

    /** @return dependency plugin name */
    public String getName() { return name; }
    /** @return Paper dependency ordering */
    public abstract Property<PaperDependencyLoadOrder> getLoad();
    /** @return whether Paper must find the dependency */
    public abstract Property<Boolean> getRequired();
    /** @return whether the other plugin's classpath is visible */
    public abstract Property<Boolean> getJoinClasspath();

    PaperPluginDependencySpec snapshot(PaperDependencyPhase phase) {
        return new PaperPluginDependencySpec(name, phase, getLoad().get(),
                getRequired().get(), getJoinClasspath().get());
    }
}
