package com.trickshotmlg.devkit.bukkit.items;

import com.trickshotmlg.devkit.bukkit.data.BukkitData;
import com.trickshotmlg.devkit.bukkit.data.DataKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.bukkit.profile.PlayerProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/** Fluent defensive builder for common Bukkit ItemStack metadata. */
public final class ItemBuilder {
    private static final List<ItemTextAdapter> TEXT_ADAPTERS = ServiceLoader.load(ItemTextAdapter.class, ItemTextAdapter.class.getClassLoader())
            .stream().map(ServiceLoader.Provider::get).toList();
    private final ItemStack prototype;

    private ItemBuilder(ItemStack item) { prototype = item; }
    /** True when an optional platform provider preserves Adventure item name/lore directly. */
    public static boolean supportsNativeAdventure() { return !TEXT_ADAPTERS.isEmpty(); }
    public static ItemBuilder of(Material material) {
        Objects.requireNonNull(material, "material");
        if (material.isAir()) throw new IllegalArgumentException("Item material cannot be air");
        return new ItemBuilder(new ItemStack(material));
    }
    public static ItemBuilder from(ItemStack item) { return new ItemBuilder(Objects.requireNonNull(item, "item").clone()); }
    public ItemBuilder amount(int amount) {
        if (amount < 1 || amount > prototype.getMaxStackSize()) throw new IllegalArgumentException("Amount must be between 1 and " + prototype.getMaxStackSize());
        prototype.setAmount(amount); return this;
    }
    public ItemBuilder name(String value) { return name(Component.text(Objects.requireNonNull(value, "name"))); }
    public ItemBuilder name(Component value) { Objects.requireNonNull(value, "name"); return edit(meta -> {
        if (TEXT_ADAPTERS.isEmpty()) meta.setDisplayName(LegacyComponentSerializer.legacySection().serialize(value));
        else TEXT_ADAPTERS.get(0).displayName(meta, value);
    }); }
    public ItemBuilder nameMiniMessage(String template) { return name(MiniMessage.miniMessage().deserialize(Objects.requireNonNull(template, "template"))); }
    public ItemBuilder lore(String... lines) {
        Objects.requireNonNull(lines, "lines");
        List<Component> values = new ArrayList<>(lines.length);
        for (String line : lines) values.add(Component.text(Objects.requireNonNull(line, "lore line")));
        return lore(values);
    }
    public ItemBuilder lore(List<Component> lines) {
        List<Component> values = List.copyOf(Objects.requireNonNull(lines, "lines"));
        return edit(meta -> {
            if (TEXT_ADAPTERS.isEmpty()) meta.setLore(values.stream().map(LegacyComponentSerializer.legacySection()::serialize).toList());
            else TEXT_ADAPTERS.get(0).lore(meta, values);
        });
    }
    public ItemBuilder loreMiniMessage(String... templates) {
        Objects.requireNonNull(templates, "templates");
        List<Component> values = new ArrayList<>(templates.length);
        for (String value : templates) values.add(MiniMessage.miniMessage().deserialize(Objects.requireNonNull(value, "template")));
        return lore(values);
    }
    public ItemBuilder enchantment(Enchantment enchantment, int level) {
        Objects.requireNonNull(enchantment, "enchantment");
        if (level < 1) throw new IllegalArgumentException("Enchantment level must be positive");
        return edit(meta -> meta.addEnchant(enchantment, level, true));
    }
    public ItemBuilder glow(boolean enabled) { return edit(meta -> meta.setEnchantmentGlintOverride(enabled)); }
    public ItemBuilder unbreakable(boolean value) { return edit(meta -> meta.setUnbreakable(value)); }
    public ItemBuilder hide(ItemFlag... flags) {
        Objects.requireNonNull(flags, "flags");
        return edit(meta -> { for (ItemFlag flag : flags) meta.addItemFlags(Objects.requireNonNull(flag, "flag")); });
    }
    public ItemBuilder hideAttributes() { return hide(ItemFlag.HIDE_ATTRIBUTES); }
    public ItemBuilder hideAll() { return hide(ItemFlag.values()); }
    public ItemBuilder damage(int value) {
        if (value < 0) throw new IllegalArgumentException("Damage cannot be negative");
        return edit(meta -> { if (!(meta instanceof Damageable damageable)) throw new IllegalArgumentException("Item type does not support damage: " + prototype.getType()); damageable.setDamage(value); });
    }
    @SuppressWarnings("deprecation") public ItemBuilder customModelData(Integer value) {
        if (value != null && value < 0) throw new IllegalArgumentException("Custom model data cannot be negative");
        return edit(meta -> meta.setCustomModelData(value));
    }
    public ItemBuilder itemModel(NamespacedKey key) { return edit(meta -> meta.setItemModel(Objects.requireNonNull(key, "key"))); }
    public ItemBuilder leatherColor(Color value) {
        Objects.requireNonNull(value, "color");
        return edit(meta -> { if (!(meta instanceof LeatherArmorMeta leather)) throw new IllegalArgumentException("Item is not leather armor: " + prototype.getType()); leather.setColor(value); });
    }
    public ItemBuilder potion(PotionType value) {
        Objects.requireNonNull(value, "type");
        return edit(meta -> { if (!(meta instanceof PotionMeta potion)) throw new IllegalArgumentException("Item is not a potion: " + prototype.getType()); potion.setBasePotionType(value); });
    }
    public ItemBuilder skullProfile(PlayerProfile value) {
        Objects.requireNonNull(value, "profile");
        return edit(meta -> { if (!(meta instanceof SkullMeta skull)) throw new IllegalArgumentException("Item is not a player head: " + prototype.getType()); skull.setOwnerProfile(value.clone()); });
    }
    public ItemBuilder persistentData(NamespacedKey key, PersistentDataType<?, ?> type, Object value) {
        Objects.requireNonNull(key, "key"); Objects.requireNonNull(type, "type"); Objects.requireNonNull(value, "value");
        return edit(meta -> setUntyped(meta, key, type, value));
    }
    public <T> ItemBuilder data(DataKey<T> key, T value) { BukkitData.set(prototype, key, value); return this; }
    public ItemBuilder removeData(DataKey<?> key) { BukkitData.remove(prototype, key); return this; }
    public ItemStack build() { return prototype.clone(); }
    private ItemBuilder edit(java.util.function.Consumer<ItemMeta> action) {
        ItemMeta meta = Objects.requireNonNull(prototype.getItemMeta(), "Item type has no metadata");
        action.accept(meta);
        prototype.setItemMeta(meta);
        return this;
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static void setUntyped(ItemMeta meta, NamespacedKey key, PersistentDataType type, Object value) {
        meta.getPersistentDataContainer().set(key, type, value);
    }
}
