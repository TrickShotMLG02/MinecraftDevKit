package com.trickshotmlg.devkit.bukkit.gui;

import org.bukkit.inventory.ItemStack;

/** Renders an independent item for a particular viewer and GUI session. */
@FunctionalInterface
public interface GuiItemTemplate {
    /**
     * Creates the item to display. The returned item is cloned before it is placed into the
     * managed inventory.
     * @param context current render context
     * @return item to render, or {@code null} to leave the slot empty
     */
    ItemStack render(GuiRenderContext context);
}
