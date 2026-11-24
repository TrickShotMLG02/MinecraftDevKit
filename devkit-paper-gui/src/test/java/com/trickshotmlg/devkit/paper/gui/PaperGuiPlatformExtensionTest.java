package com.trickshotmlg.devkit.paper.gui;

import com.trickshotmlg.devkit.bukkit.gui.GuiPlatformCapabilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperGuiPlatformExtensionTest {
    @Test
    void extensionAdvertisesNativeAdventureCapabilities() {
        assertTrue(GuiPlatformCapabilities.supportsNativeAdventureComponents());
    }
}
