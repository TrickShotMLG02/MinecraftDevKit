package com.trickshotmlg.devkit.bukkit.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/** Small factories for common decorative GUI items. */
public final class GuiItems {
    private GuiItems() { }
    public static ItemStack of(Material material, String plainName) {
        return ItemBuilder.of(material).name(Objects.requireNonNull(plainName, "plainName")).build();
    }
    public static ItemStack pane(Material material, Component name) {
        return ItemBuilder.of(material).name(Objects.requireNonNull(name, "name")).build();
    }
    public static ItemStack grayPane() { return of(Material.GRAY_STAINED_GLASS_PANE, " "); }
    public static ItemStack blackPane() { return of(Material.BLACK_STAINED_GLASS_PANE, " "); }
    public static ItemStack greenPane(String name) { return of(Material.GREEN_STAINED_GLASS_PANE, name); }
    public static ItemStack redPane(String name) { return of(Material.RED_STAINED_GLASS_PANE, name); }
}
