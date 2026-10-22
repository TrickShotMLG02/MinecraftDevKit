package com.trickshotmlg.devkit.bukkit.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Objects;

/** Information and safe operations available to a GUI click handler. */
public record GuiClickContext(Player viewer, GuiMenu menu, GuiSession session, int slot,
                              ClickType clickType, InventoryClickEvent event, GuiManager manager) {
    public GuiClickContext {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(clickType, "clickType");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(manager, "manager");
    }

    public void open(GuiMenu child) { manager.navigate(session, Objects.requireNonNull(child, "child")); }
    public void replace(GuiMenu menu) { manager.replace(session, Objects.requireNonNull(menu, "menu")); }
    public void back() { manager.back(session); }
    public void close() { manager.close(session); }
    public void refresh() { manager.refresh(session); }
    public void refreshSlot(int slot) { manager.refreshSlot(session, slot); }
}
