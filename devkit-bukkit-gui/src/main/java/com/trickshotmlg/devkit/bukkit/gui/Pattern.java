package com.trickshotmlg.devkit.bukkit.gui;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

/** Reusable background pattern factories. Pattern items are cloned before display. */
public final class Pattern {
    private Pattern() { }
    public static GuiPattern solid(ItemStack item) {
        ItemStack copy = Objects.requireNonNull(item, "item").clone();
        return ignored -> copy.clone();
    }
    public static GuiPattern checkerboard(ItemStack first, ItemStack second) {
        ItemStack a = Objects.requireNonNull(first, "first").clone(), b = Objects.requireNonNull(second, "second").clone();
        return context -> ((context.row() + context.column()) & 1) == 0 ? a.clone() : b.clone();
    }
    public static GuiPattern border(ItemStack item) {
        ItemStack copy = Objects.requireNonNull(item, "item").clone();
        return context -> context.row() == 0 || context.row() == context.rows() - 1 || context.column() == 0 || context.column() == 8
                ? copy.clone() : null;
    }
    public static GuiPattern rows(List<ItemStack> items) {
        List<ItemStack> copies = items.stream().map(item -> Objects.requireNonNull(item, "item").clone()).toList();
        if (copies.isEmpty()) throw new IllegalArgumentException("At least one row item is required");
        return context -> copies.get(context.row() % copies.size()).clone();
    }
    public static GuiPattern columns(List<ItemStack> items) {
        List<ItemStack> copies = items.stream().map(item -> Objects.requireNonNull(item, "item").clone()).toList();
        if (copies.isEmpty()) throw new IllegalArgumentException("At least one column item is required");
        return context -> copies.get(context.column() % copies.size()).clone();
    }
    public static GuiPattern custom(GuiPattern pattern) { return Objects.requireNonNull(pattern, "pattern"); }
}
