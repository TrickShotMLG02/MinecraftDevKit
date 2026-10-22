package com.trickshotmlg.devkit.bukkit.gui;

import com.trickshotmlg.devkit.text.TextProvider;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Objects;
import java.util.UUID;

/** Mutable state for one viewer; never shared between two players. */
public final class GuiSession {
    private final UUID viewerId;
    private final Player viewer;
    private final TextProvider textProvider;
    private GuiMenu menu;
    private Inventory inventory;
    private boolean transitioning;
    private boolean closed;

    GuiSession(Player viewer, GuiMenu menu, TextProvider textProvider) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        viewerId = viewer.getUniqueId();
        this.menu = Objects.requireNonNull(menu, "menu");
        this.textProvider = textProvider;
    }
    public UUID viewerId() { return viewerId; }
    public Player viewer() { return viewer; }
    public GuiMenu menu() { return menu; }
    public boolean closed() { return closed; }
    TextProvider textProvider() { return textProvider; }
    Inventory inventory() { return inventory; }
    void menu(GuiMenu value) { menu = value; }
    void inventory(Inventory value) { inventory = value; }
    boolean transitioning() { return transitioning; }
    void transitioning(boolean value) { transitioning = value; }
    void close() { closed = true; inventory = null; }
}
