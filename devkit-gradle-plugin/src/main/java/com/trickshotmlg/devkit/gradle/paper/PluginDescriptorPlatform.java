package com.trickshotmlg.devkit.gradle.paper;

/** Selects the descriptor emitted by the MinecraftDevKit Gradle plugin. */
public enum PluginDescriptorPlatform {
    /** Traditional Bukkit-compatible plugin metadata in {@code plugin.yml}. */
    BUKKIT,
    /** Native Paper plugin metadata in {@code paper-plugin.yml}. */
    PAPER_NATIVE
}
