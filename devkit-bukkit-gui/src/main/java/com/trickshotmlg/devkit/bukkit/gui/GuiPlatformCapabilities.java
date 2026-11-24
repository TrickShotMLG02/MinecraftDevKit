package com.trickshotmlg.devkit.bukkit.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.ServiceLoader;

/** Reports optional GUI enhancements supplied by a platform extension. */
public final class GuiPlatformCapabilities {
    private static final List<BukkitGuiPlatformExtension> EXTENSIONS = ServiceLoader
            .load(BukkitGuiPlatformExtension.class, BukkitGuiPlatformExtension.class.getClassLoader())
            .stream().map(ServiceLoader.Provider::get).toList();

    private GuiPlatformCapabilities() { }

    /** Returns whether an optional extension can retain rich Adventure metadata instead of legacy serialization. */
    public static boolean supportsNativeAdventureComponents() {
        return EXTENSIONS.stream().anyMatch(BukkitGuiPlatformExtension::supportsNativeComponents);
    }

    static Inventory createInventory(InventoryHolder holder, int size, Component title) {
        for (BukkitGuiPlatformExtension extension : EXTENSIONS) {
            if (extension.supportsNativeComponents()) return extension.createInventory(holder, size, title);
        }
        return Bukkit.createInventory(holder, size, legacyString(title));
    }

    static void displayName(ItemMeta meta, Component name) {
        for (BukkitGuiPlatformExtension extension : EXTENSIONS) {
            if (extension.supportsNativeComponents()) {
                extension.displayName(meta, name);
                return;
            }
        }
        meta.setDisplayName(legacyString(name));
    }

    static void lore(ItemMeta meta, List<Component> lines) {
        for (BukkitGuiPlatformExtension extension : EXTENSIONS) {
            if (extension.supportsNativeComponents()) {
                extension.lore(meta, lines);
                return;
            }
        }
        meta.setLore(lines.stream().map(GuiPlatformCapabilities::legacyString).toList());
    }

    static String legacyString(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
