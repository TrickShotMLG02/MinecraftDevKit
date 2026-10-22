package com.trickshotmlg.devkit.bukkit.gui;

import org.bukkit.Material;

/** Factories for small common navigation controls. */
public final class GuiButtons {
    private GuiButtons() { }
    public static GuiButton back() { return GuiButton.of(GuiItems.of(Material.ARROW, "Back"), GuiClickContext::back); }
    public static GuiButton close() { return GuiButton.of(GuiItems.of(Material.BARRIER, "Close"), GuiClickContext::close); }
    public static GuiButton refresh() { return GuiButton.of(GuiItems.of(Material.SUNFLOWER, "Refresh"), GuiClickContext::refresh); }
}
