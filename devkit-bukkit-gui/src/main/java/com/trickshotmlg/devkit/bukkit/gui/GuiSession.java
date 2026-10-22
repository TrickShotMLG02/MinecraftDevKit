package com.trickshotmlg.devkit.bukkit.gui;

import com.trickshotmlg.devkit.text.TextProvider;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Objects;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;

/** Mutable state for one viewer; never shared between two players. */
public final class GuiSession {
    private final UUID viewerId;
    private final Player viewer;
    private final TextProvider textProvider;
    private final GuiManager manager;
    private final Deque<GuiMenu> history = new ArrayDeque<>();
    private final Map<GuiStateKey<?>, Object> state = new IdentityHashMap<>();
    private GuiMenu menu;
    private Inventory inventory;
    private boolean transitioning;
    private boolean closed;
    private long navigationVersion;

    GuiSession(Player viewer, GuiMenu menu, TextProvider textProvider, GuiManager manager) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        viewerId = viewer.getUniqueId();
        this.menu = Objects.requireNonNull(menu, "menu");
        this.textProvider = textProvider;
        this.manager = Objects.requireNonNull(manager, "manager");
    }
    public UUID viewerId() { return viewerId; }
    public Player viewer() { return viewer; }
    public GuiMenu menu() { return menu; }
    public boolean closed() { return closed; }
    public int historyDepth() { return history.size(); }
    /** Reads a typed session-local value, returning its key's initial value if unset. */
    @SuppressWarnings("unchecked") public <T> T get(GuiStateKey<T> key) {
        Objects.requireNonNull(key, "key");
        return (T) state.getOrDefault(key, key.initialValue());
    }
    /** Sets a non-null session-local value and refreshes this session's open menu. */
    public <T> void set(GuiStateKey<T> key, T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        manager.requireSessionThread();
        if (closed) throw new IllegalStateException("GUI session is closed");
        state.put(key, value);
        manager.refresh(this);
    }
    TextProvider textProvider() { return textProvider; }
    Inventory inventory() { return inventory; }
    void menu(GuiMenu value) { menu = value; }
    Deque<GuiMenu> history() { return history; }
    void inventory(Inventory value) { inventory = value; }
    boolean transitioning() { return transitioning; }
    void transitioning(boolean value) { transitioning = value; }
    long nextNavigationVersion() { return ++navigationVersion; }
    long navigationVersion() { return navigationVersion; }
    void close() { closed = true; inventory = null; }
}
