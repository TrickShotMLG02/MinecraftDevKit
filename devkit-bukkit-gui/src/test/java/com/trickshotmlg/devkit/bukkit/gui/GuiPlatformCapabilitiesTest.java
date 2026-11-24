package com.trickshotmlg.devkit.bukkit.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiPlatformCapabilitiesTest {
    @Test
    void commonClasspathUsesLegacyRenderingWithoutPaperExtension() {
        org.junit.jupiter.api.Assertions.assertFalse(GuiPlatformCapabilities.supportsNativeAdventureComponents());
        assertEquals("\u00a7aReady", GuiPlatformCapabilities.legacyString(Component.text("Ready", NamedTextColor.GREEN)));
    }
}
