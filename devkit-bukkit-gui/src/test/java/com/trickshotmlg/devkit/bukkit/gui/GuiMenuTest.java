package com.trickshotmlg.devkit.bukkit.gui;

import com.trickshotmlg.devkit.text.TextRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuiMenuTest {
    @Test
    void chestRowsAreRestrictedToSupportedChestSizes() {
        for (int rows = 1; rows <= 6; rows++) {
            GuiMenu menu = GuiMenu.chest(rows).build();
            assertEquals(rows, menu.rows());
            assertEquals(rows * 9, menu.size());
        }
        assertThrows(IllegalArgumentException.class, () -> GuiMenu.chest(0));
        assertThrows(IllegalArgumentException.class, () -> GuiMenu.chest(7));
    }

    @Test
    void componentSlotsAreValidatedAndCannotBeAccidentallyOverwritten() {
        GuiMenu.Builder builder = GuiMenu.chest(2);
        GuiComponent marker = new GuiComponent() {
            @Override public org.bukkit.inventory.ItemStack render(GuiRenderContext context) { return null; }
        };
        builder.component(0, marker).component(17, marker);
        assertThrows(IllegalArgumentException.class, () -> builder.component(18, marker));
        assertThrows(IllegalArgumentException.class, () -> builder.component(-1, marker));
        assertThrows(IllegalArgumentException.class, () -> builder.component(0, marker));
        assertEquals(2, builder.build().components().size());
    }

    @Test
    void completedMenuIsImmutableAndTextRefTitleIsAccepted() {
        GuiMenu menu = GuiMenu.chest(1)
                .title(TextRef.translated("menu.title", "Example"))
                .build();
        assertTrue(menu.components().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> menu.components().put(0, context -> null));
    }
}
