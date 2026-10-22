package com.trickshotmlg.devkit.bukkit.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Color;
import org.bukkit.potion.PotionType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Fluent, defensive builder for Bukkit item stacks. Text is plain unless a component or explicit
 * MiniMessage method is used. Each call to {@link #build()} returns an independent stack. */
public final class ItemBuilder {
    private final ItemStack prototype;

    private ItemBuilder(ItemStack item) { prototype = item; }

    public static ItemBuilder of(Material material) {
        Objects.requireNonNull(material, "material");
        if (material.isAir()) throw new IllegalArgumentException("Item material cannot be air");
        return new ItemBuilder(new ItemStack(material));
    }

    /** Copies an existing item; later edits to the original do not affect this builder. */
    public static ItemBuilder from(ItemStack item) {
        return new ItemBuilder(Objects.requireNonNull(item, "item").clone());
    }

    public ItemBuilder amount(int amount) {
        if (amount < 1 || amount > prototype.getMaxStackSize())
            throw new IllegalArgumentException("Amount must be between 1 and " + prototype.getMaxStackSize());
        prototype.setAmount(amount);
        return this;
    }

    public ItemBuilder name(String plainText) { return edit(meta -> meta.displayName(Component.text(Objects.requireNonNull(plainText, "name")))); }
    public ItemBuilder name(Component component) { return edit(meta -> meta.displayName(Objects.requireNonNull(component, "name"))); }
    /** Parses an explicitly trusted MiniMessage formatting template. */
    public ItemBuilder nameMiniMessage(String template) {
        return name(MiniMessage.miniMessage().deserialize(Objects.requireNonNull(template, "template")));
    }

    public ItemBuilder lore(String... lines) {
        Objects.requireNonNull(lines, "lines");
        List<Component> components = new ArrayList<>(lines.length);
        for (String line : lines) components.add(Component.text(Objects.requireNonNull(line, "lore line")));
        return lore(components);
    }
    public ItemBuilder lore(List<Component> lines) {
        List<Component> copy = List.copyOf(Objects.requireNonNull(lines, "lines"));
        return edit(meta -> meta.lore(copy));
    }
    /** Parses explicitly trusted MiniMessage formatting templates. */
    public ItemBuilder loreMiniMessage(String... templates) {
        Objects.requireNonNull(templates, "templates");
        List<Component> components = new ArrayList<>(templates.length);
        for (String template : templates) components.add(MiniMessage.miniMessage().deserialize(Objects.requireNonNull(template, "template")));
        return lore(components);
    }

    public ItemBuilder enchantment(Enchantment enchantment, int level) {
        Objects.requireNonNull(enchantment, "enchantment");
        if (level < 1) throw new IllegalArgumentException("Enchantment level must be positive");
        return edit(meta -> meta.addEnchant(enchantment, level, true));
    }
    public ItemBuilder glow(boolean enabled) {
        return edit(meta -> meta.setEnchantmentGlintOverride(enabled ? Boolean.TRUE : null));
    }
    public ItemBuilder unbreakable(boolean value) { return edit(meta -> meta.setUnbreakable(value)); }
    public ItemBuilder hide(ItemFlag... flags) {
        Objects.requireNonNull(flags, "flags");
        return edit(meta -> {
            for (ItemFlag flag : flags) meta.addItemFlags(Objects.requireNonNull(flag, "flag"));
        });
    }
    public ItemBuilder hideAttributes() { return hide(ItemFlag.HIDE_ATTRIBUTES); }
    public ItemBuilder hideAll() { return hide(ItemFlag.values()); }
    public ItemBuilder damage(int damage) {
        if (damage < 0) throw new IllegalArgumentException("Damage cannot be negative");
        return edit(meta -> {
            if (!(meta instanceof Damageable damageable)) throw new IllegalArgumentException("Item type does not support damage: " + prototype.getType());
            damageable.setDamage(damage);
        });
    }
    /** Sets legacy custom model data where the targeted API exposes it. */
    @SuppressWarnings("deprecation")
    public ItemBuilder customModelData(Integer value) {
        if (value != null && value < 0) throw new IllegalArgumentException("Custom model data cannot be negative");
        return edit(meta -> meta.setCustomModelData(value));
    }
    /** Sets the modern Paper item-model key used by 1.21.4+ data components. */
    public ItemBuilder itemModel(NamespacedKey key) { return edit(meta -> meta.setItemModel(Objects.requireNonNull(key, "key"))); }
    public ItemBuilder leatherColor(Color color) {
        Objects.requireNonNull(color, "color");
        return edit(meta -> {
            if (!(meta instanceof LeatherArmorMeta leather)) throw new IllegalArgumentException("Item is not leather armor: " + prototype.getType());
            leather.setColor(color);
        });
    }
    public ItemBuilder potion(PotionType type) {
        Objects.requireNonNull(type, "type");
        return edit(meta -> {
            if (!(meta instanceof PotionMeta potion)) throw new IllegalArgumentException("Item is not a potion: " + prototype.getType());
            potion.setBasePotionType(type);
        });
    }
    public ItemBuilder skullProfile(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return edit(meta -> {
            if (!(meta instanceof SkullMeta skull)) throw new IllegalArgumentException("Item is not a player head: " + prototype.getType());
            skull.setOwnerProfile(profile.clone());
        });
    }
    public ItemBuilder skullTextureHash(String hash) { return skullProfile(SkullTextureService.textureProfile(Objects.requireNonNull(hash, "hash"))); }
    public ItemBuilder skullTextureUrl(String url) { return skullProfile(SkullTextureService.textureProfile(Objects.requireNonNull(url, "url"))); }
    public ItemBuilder skullTextureBase64(String base64) {
        return skullProfile(SkullTextureService.textureProfile(SkullTextureService.validateBase64Texture(base64).toExternalForm()));
    }
    public <P, C> ItemBuilder persistentData(NamespacedKey key, PersistentDataType<P, C> type, C value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        return edit(meta -> meta.getPersistentDataContainer().set(key, type, value));
    }

    /** Returns a defensive copy independent of this builder and every other built item. */
    public ItemStack build() { return prototype.clone(); }

    private ItemBuilder edit(java.util.function.Consumer<ItemMeta> edit) {
        ItemMeta meta = Objects.requireNonNull(prototype.getItemMeta(), "Item type has no metadata");
        edit.accept(meta);
        prototype.setItemMeta(meta);
        return this;
    }
}
