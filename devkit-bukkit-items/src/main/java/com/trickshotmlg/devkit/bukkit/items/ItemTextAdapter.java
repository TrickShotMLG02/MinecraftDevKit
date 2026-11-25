package com.trickshotmlg.devkit.bukkit.items;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Optional platform implementation for retaining native Adventure item metadata. */
public interface ItemTextAdapter {
    void displayName(ItemMeta meta, Component component);
    void lore(ItemMeta meta, List<Component> components);
}
