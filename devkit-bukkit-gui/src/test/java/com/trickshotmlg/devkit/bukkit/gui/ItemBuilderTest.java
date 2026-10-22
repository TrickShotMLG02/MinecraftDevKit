package com.trickshotmlg.devkit.bukkit.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemBuilderTest {
    @Test
    void builtStacksAndSourceItemsAreIndependent() {
        ItemStack source = new ItemStack(Material.DIAMOND, 2);
        ItemBuilder builder = ItemBuilder.from(source);
        source.setAmount(4);

        ItemStack first = builder.build();
        first.setAmount(7);
        ItemStack second = builder.build();
        assertEquals(2, second.getAmount());
        assertEquals(Material.DIAMOND, second.getType());
    }

    @Test
    void amountAndMaterialAreValidated() {
        ItemBuilder builder = ItemBuilder.of(Material.DIAMOND).amount(3);
        assertEquals(3, builder.build().getAmount());
        assertThrows(IllegalArgumentException.class, () -> ItemBuilder.of(Material.AIR));
        assertThrows(IllegalArgumentException.class, () -> builder.amount(0));
        assertThrows(IllegalArgumentException.class, () -> builder.amount(65));
    }
}
