package com.trickshotmlg.devkit.gradle.paper;

import java.io.Serializable;

/** Immutable task input for one Paper bootstrap- or server-phase dependency. */
public record PaperPluginDependencySpec(String name, PaperDependencyPhase phase,
                                        PaperDependencyLoadOrder load, boolean required,
                                        boolean joinClasspath) implements Serializable { }
