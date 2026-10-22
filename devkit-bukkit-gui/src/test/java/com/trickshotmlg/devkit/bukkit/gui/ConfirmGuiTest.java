package com.trickshotmlg.devkit.bukkit.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfirmGuiTest {
    @Test
    void failedOrReentrantConfirmationAttemptsCannotRunAgain() {
        assertTrue(ConfirmGui.canAttempt(false));
        assertFalse(ConfirmGui.canAttempt(true));
    }
}
