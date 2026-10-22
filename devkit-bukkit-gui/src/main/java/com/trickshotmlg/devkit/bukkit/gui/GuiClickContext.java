package com.trickshotmlg.devkit.bukkit.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Objects;

/** Information and safe operations available to a GUI click handler. */
public record GuiClickContext(Player viewer, GuiMenu menu, GuiSession session, int slot,
                              ClickType clickType, InventoryClickEvent event) {
    public GuiClickContext {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(clickType, "clickType");
        Objects.requireNonNull(event, "event");
    }
}
