package com.trickshotmlg.devkit.paper.gui;

import com.trickshotmlg.devkit.bukkit.gui.BukkitGuiPlatformExtension;
import com.trickshotmlg.devkit.bukkit.feedback.FeedbackPlatformExtension;
import com.trickshotmlg.devkit.bukkit.feedback.FeedbackTimings;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;
import net.kyori.adventure.title.Title;
import java.time.Duration;

import java.util.List;

/** Paper implementation of optional native GUI metadata capabilities. */
public final class PaperGuiPlatformExtension implements BukkitGuiPlatformExtension,
        com.trickshotmlg.devkit.bukkit.items.ItemTextAdapter, FeedbackPlatformExtension {
    @Override public boolean supportsNativeComponents() { return true; }

    @Override public Inventory createInventory(InventoryHolder holder, int size, Component title) {
        return Bukkit.createInventory(holder, size, title);
    }

    @Override public void displayName(ItemMeta meta, Component name) { meta.displayName(name); }
    @Override public void lore(ItemMeta meta, List<Component> lines) { meta.lore(lines); }
    @Override public void message(Player player, Component component) { player.sendMessage(component); }
    @Override public void actionBar(Player player, Component component) { player.sendActionBar(component); }
    @Override public void title(Player player, Component title, Component subtitle, FeedbackTimings timings) {
        player.showTitle(Title.title(title, subtitle, Title.Times.times(
                Duration.ofMillis(timings.fadeInTicks() * 50L), Duration.ofMillis(timings.stayTicks() * 50L),
                Duration.ofMillis(timings.fadeOutTicks() * 50L))));
    }
    @Override public void clearTitle(Player player) { player.clearTitle(); }
}
