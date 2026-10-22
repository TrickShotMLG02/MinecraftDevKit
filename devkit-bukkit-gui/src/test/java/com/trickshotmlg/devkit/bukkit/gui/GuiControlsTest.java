package com.trickshotmlg.devkit.bukkit.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuiControlsTest {
    @Test
    void toggleTransitionsAlwaysFlipTheCurrentValue() {
        assertTrue(ToggleButton.nextValue(false));
        assertFalse(ToggleButton.nextValue(true));
    }

    @Test
    void requiredRadioSelectionCannotBeAccidentallyCleared() {
        assertEquals("EASY", RadioGroup.nextSelection("EASY", "EASY", false));
        assertEquals("HARD", RadioGroup.nextSelection("EASY", "HARD", false));
        assertNull(RadioGroup.nextSelection("EASY", "EASY", true));
    }

    @Test
    void radioGroupValidatesInitialChoiceAndKeepsTypedSlots() {
        var builder = RadioGroup.<String>builder()
                .option(10, "EASY", ignored -> null, ignored -> null, null, ignored -> true, null)
                .option(11, "HARD", ignored -> null, ignored -> null, null, ignored -> true, null);
        assertThrows(IllegalStateException.class, builder::build);
        RadioGroup<String> group = builder.selected("EASY").build();
        assertEquals(2, group.components().size());
        assertThrows(IllegalArgumentException.class, () -> builder.option(12, "EASY", ignored -> null,
                ignored -> null, null, ignored -> true, null));
        assertThrows(IllegalArgumentException.class, () -> builder.option(10, "NORMAL", ignored -> null,
                ignored -> null, null, ignored -> true, null));
        builder.option(12, "NORMAL", ignored -> null, ignored -> null, null, ignored -> true, null);
        assertEquals(3, builder.selected("NORMAL").build().components().size());
    }
}
