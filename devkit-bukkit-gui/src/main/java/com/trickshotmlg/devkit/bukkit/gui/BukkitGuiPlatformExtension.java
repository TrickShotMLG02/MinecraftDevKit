package com.trickshotmlg.devkit.bukkit.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Optional implementation of GUI operations that are richer on a particular Bukkit platform. */
public interface BukkitGuiPlatformExtension {
    /** Whether this implementation preserves Adventure components directly in GUI metadata. */
    boolean supportsNativeComponents();

    /** Creates an inventory with a native Adventure title. Called only when native components are supported. */
    default Inventory createInventory(InventoryHolder holder, int size, Component title) {
        throw new UnsupportedOperationException("Native Adventure inventory titles are not supported");
    }

    /** Applies a display name without converting it to a legacy string. */
    default void displayName(ItemMeta meta, Component name) {
        throw new UnsupportedOperationException("Native Adventure item names are not supported");
    }

    /** Applies lore without converting it to legacy strings. */
    default void lore(ItemMeta meta, List<Component> lines) {
        throw new UnsupportedOperationException("Native Adventure item lore is not supported");
    }

}
