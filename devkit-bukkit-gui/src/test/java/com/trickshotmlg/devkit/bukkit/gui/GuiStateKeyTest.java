package com.trickshotmlg.devkit.bukkit.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuiStateKeyTest {
    @Test
    void keysHaveStableNamesAndRejectInvalidDefaults() {
        GuiStateKey<Boolean> enabled = GuiStateKey.of("settings.enabled", false);
        assertEquals("settings.enabled", enabled.name());
        assertFalse(enabled.initialValue());
        assertThrows(IllegalArgumentException.class, () -> GuiStateKey.of(" ", true));
        assertThrows(NullPointerException.class, () -> GuiStateKey.of("nullable", null));
        assertNotEquals(GuiStateKey.of("same", 1), GuiStateKey.of("same", 1));
    }
}
