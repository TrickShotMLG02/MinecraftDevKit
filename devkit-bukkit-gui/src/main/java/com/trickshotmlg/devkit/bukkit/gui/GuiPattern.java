package com.trickshotmlg.devkit.bukkit.gui;

import org.bukkit.inventory.ItemStack;

/** Computes an optional background item for each inventory slot. */
@FunctionalInterface
public interface GuiPattern {
    ItemStack itemAt(GuiPatternContext context);
}
