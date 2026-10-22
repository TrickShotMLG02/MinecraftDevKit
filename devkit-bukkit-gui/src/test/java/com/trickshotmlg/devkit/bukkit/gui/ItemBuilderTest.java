package com.trickshotmlg.devkit.bukkit.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemBuilderTest {
    @Test
    void entryPointsRejectNullWithoutRequiringAPlatformRegistry() {
        assertThrows(NullPointerException.class, () -> ItemBuilder.of(null));
        assertThrows(NullPointerException.class, () -> ItemBuilder.from(null));
    }
}
