package com.trickshotmlg.devkit.bukkit.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageInfoTest {
    @Test
    void calculatesOneBasedPagesAndClampsAfterContentShrinks() {
        assertEquals(new PageInfo(1, 1, 0, 10, 0, 0, false, false), PageInfo.of(1, 0, 10));
        assertEquals(new PageInfo(2, 3, 25, 10, 10, 10, true, true), PageInfo.of(2, 25, 10));
        assertEquals(new PageInfo(3, 3, 25, 10, 20, 5, true, false), PageInfo.of(99, 25, 10));
        assertEquals(1, PageInfo.of(8, 1, 10).currentPage());
    }

    @Test
    void rejectsInvalidPaginationInputs() {
        assertThrows(IllegalArgumentException.class, () -> PageInfo.of(0, 1, 10));
        assertThrows(IllegalArgumentException.class, () -> PageInfo.of(1, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> PageInfo.of(1, 1, 0));
    }
}
