package com.trickshotmlg.devkit.bukkit.gui;

import org.bukkit.inventory.ItemStack;

/** Immutable component rendered into one inventory slot. */
public interface GuiComponent {
    /** Renders this component for the current session. */
    ItemStack render(GuiRenderContext context);

    /** Whether this component should currently be shown. */
    default boolean visible(GuiRenderContext context) { return true; }

    /** Invoked only for visible, permitted components with a click handler. */
    default void click(GuiClickContext context) { }

    /** Whether this component handles interaction rather than being decorative. */
    default boolean interactive() { return false; }
}
