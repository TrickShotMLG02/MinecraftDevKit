package com.trickshotmlg.devkit.bukkit.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SlotsTest {
    @Test
    void helpersUseZeroBasedCoordinatesAndInclusiveRectangles() {
        assertEquals(List.of(9, 10, 11, 12, 13, 14, 15, 16, 17), Slots.row(1));
        assertEquals(List.of(2, 11, 20), Slots.column(2, 3));
        assertEquals(List.of(10, 11, 19, 20), Slots.rectangle(1, 1, 2, 2));
        assertEquals(26, Slots.border(6).size());
        assertEquals(List.of(2, 4, 6), Slots.of(2, 4, 6));
    }

    @Test
    void rejectsInvalidCoordinatesAndDuplicateSlots() {
        assertThrows(IllegalArgumentException.class, () -> Slots.row(6));
        assertThrows(IllegalArgumentException.class, () -> Slots.column(9, 2));
        assertThrows(IllegalArgumentException.class, () -> Slots.rectangle(2, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Slots.of(1, 1));
    }
}
