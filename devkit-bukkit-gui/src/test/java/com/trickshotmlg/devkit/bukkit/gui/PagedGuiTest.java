package com.trickshotmlg.devkit.bukkit.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PagedGuiTest {
    @Test
    void buildsAStableMenuFromExplicitSlotsAndControls() {
        GuiMenu menu = PagedGui.<String>chest(3)
                .contentSlots(List.of(10, 11, 12))
                .items(context -> List.of("A", "B"))
                .render((context, value) -> null)
                .previousPage(18).pageIndicator(22).nextPage(26)
                .build();
        assertEquals(3, menu.rows());
        assertEquals(6, menu.components().size());
        assertThrows(UnsupportedOperationException.class, () -> menu.components().clear());
    }

    @Test
    void rejectsDuplicateContentSlotsAndOverlappingControls() {
        assertThrows(IllegalArgumentException.class, () -> PagedGui.<String>chest(3)
                .contentSlots(List.of(10, 10)).items(context -> List.of()).render((context, item) -> null).build());
        assertThrows(IllegalArgumentException.class, () -> PagedGui.<String>chest(3)
                .contentSlots(List.of(10)).items(context -> List.of()).render((context, item) -> null)
                .previousPage(10).build());
        assertThrows(IllegalArgumentException.class, () -> PagedGui.<String>chest(1)
                .items(context -> List.of()).render((context, item) -> null).build());
    }
}
