package com.trickshotmlg.devkit.bukkit.gui;

import com.trickshotmlg.devkit.bukkit.data.DataKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.bukkit.profile.PlayerProfile;

import java.util.List;

/** Backwards-compatible GUI-package facade for the reusable Bukkit item builder. */
public final class ItemBuilder {
    private final com.trickshotmlg.devkit.bukkit.items.ItemBuilder delegate;
    private ItemBuilder(com.trickshotmlg.devkit.bukkit.items.ItemBuilder delegate) { this.delegate = delegate; }
    public static boolean supportsNativeAdventure() { return com.trickshotmlg.devkit.bukkit.items.ItemBuilder.supportsNativeAdventure(); }
    public static ItemBuilder of(Material material) { return new ItemBuilder(com.trickshotmlg.devkit.bukkit.items.ItemBuilder.of(material)); }
    public static ItemBuilder from(ItemStack item) { return new ItemBuilder(com.trickshotmlg.devkit.bukkit.items.ItemBuilder.from(item)); }
    public ItemBuilder amount(int amount) { delegate.amount(amount); return this; }
    public ItemBuilder name(String text) { delegate.name(text); return this; }
    public ItemBuilder name(Component component) { delegate.name(component); return this; }
    public ItemBuilder nameMiniMessage(String template) { delegate.nameMiniMessage(template); return this; }
    public ItemBuilder lore(String... lines) { delegate.lore(lines); return this; }
    public ItemBuilder lore(List<Component> lines) { delegate.lore(lines); return this; }
    public ItemBuilder loreMiniMessage(String... templates) { delegate.loreMiniMessage(templates); return this; }
    public ItemBuilder enchantment(Enchantment enchantment, int level) { delegate.enchantment(enchantment, level); return this; }
    public ItemBuilder glow(boolean enabled) { delegate.glow(enabled); return this; }
    public ItemBuilder unbreakable(boolean enabled) { delegate.unbreakable(enabled); return this; }
    public ItemBuilder hide(ItemFlag... flags) { delegate.hide(flags); return this; }
    public ItemBuilder hideAttributes() { delegate.hideAttributes(); return this; }
    public ItemBuilder hideAll() { delegate.hideAll(); return this; }
    public ItemBuilder damage(int damage) { delegate.damage(damage); return this; }
    public ItemBuilder customModelData(Integer value) { delegate.customModelData(value); return this; }
    public ItemBuilder itemModel(NamespacedKey key) { delegate.itemModel(key); return this; }
    public ItemBuilder leatherColor(Color color) { delegate.leatherColor(color); return this; }
    public ItemBuilder potion(PotionType type) { delegate.potion(type); return this; }
    public ItemBuilder skullProfile(PlayerProfile profile) { delegate.skullProfile(profile); return this; }
    public ItemBuilder skullTextureHash(String hash) { delegate.skullProfile(SkullTextureService.textureProfile(hash)); return this; }
    public ItemBuilder skullTextureUrl(String url) { delegate.skullProfile(SkullTextureService.textureProfile(url)); return this; }
    public ItemBuilder skullTextureBase64(String value) { delegate.skullProfile(SkullTextureService.textureProfile(SkullTextureService.validateBase64Texture(value).toExternalForm())); return this; }
    public <P, C> ItemBuilder persistentData(NamespacedKey key, PersistentDataType<P, C> type, C value) { delegate.persistentData(key, type, value); return this; }
    public <T> ItemBuilder data(DataKey<T> key, T value) { delegate.data(key, value); return this; }
    public ItemStack build() { return delegate.build(); }
    static Boolean glintOverride(boolean enabled) { return enabled ? Boolean.TRUE : Boolean.FALSE; }
}
