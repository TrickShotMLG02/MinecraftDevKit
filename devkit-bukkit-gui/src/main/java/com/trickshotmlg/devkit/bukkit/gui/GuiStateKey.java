package com.trickshotmlg.devkit.bukkit.gui;

import java.util.Objects;

/** Identity-based, typed key for values scoped to one player's GUI session. */
public final class GuiStateKey<T> {
    private final String name;
    private final T initialValue;
    private GuiStateKey(String name, T initialValue) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("State key name cannot be blank");
        this.initialValue = Objects.requireNonNull(initialValue, "initialValue");
    }
    public static <T> GuiStateKey<T> of(String name, T initialValue) { return new GuiStateKey<>(name, initialValue); }
    public String name() { return name; }
    T initialValue() { return initialValue; }
    @Override public String toString() { return "GuiStateKey[" + name + "]"; }
}
