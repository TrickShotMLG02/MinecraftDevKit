package com.trickshotmlg.devkit.paper.gui;

import com.trickshotmlg.devkit.bukkit.gui.BukkitGuiPlatformExtension;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Paper implementation of optional native GUI metadata capabilities. */
public final class PaperGuiPlatformExtension implements BukkitGuiPlatformExtension {
    @Override public boolean supportsNativeComponents() { return true; }

    @Override public Inventory createInventory(InventoryHolder holder, int size, Component title) {
        return Bukkit.createInventory(holder, size, title);
    }

    @Override public void displayName(ItemMeta meta, Component name) { meta.displayName(name); }
    @Override public void lore(ItemMeta meta, List<Component> lines) { meta.lore(lines); }
}
