package com.trickshotmlg.devkit.bukkit.data;

import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.Optional;

/** Typed operations over Bukkit PDC holders. Mutations must run in a thread context safe for the holder. */
public final class BukkitData {
    private BukkitData() { }

    public static <T> void set(PersistentDataHolder holder, DataKey<T> key, T value) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(value, "value");
        set(holder.getPersistentDataContainer(), key, value);
    }

    public static <T> void set(PersistentDataContainer container, DataKey<T> key, T value) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(value, "value");
        setCaptured(container, Objects.requireNonNull(key, "key"), value);
    }

    private static <P, T> void setCaptured(PersistentDataContainer container, DataKey<T> key, T value) {
        container.set(key.namespacedKey(), (PersistentDataType<P, T>) key.codec().persistentType(), defensive(value));
    }

    public static <T> Optional<T> get(PersistentDataHolder holder, DataKey<T> key) {
        return get(Objects.requireNonNull(holder, "holder").getPersistentDataContainer(), key);
    }

    public static <T> Optional<T> get(PersistentDataContainer container, DataKey<T> key) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(key, "key");
        return getCaptured(container, key);
    }

    private static <P, T> Optional<T> getCaptured(PersistentDataContainer container, DataKey<T> key) {
        PersistentDataType<P, T> type = (PersistentDataType<P, T>) key.codec().persistentType();
        if (!container.has(key.namespacedKey())) return Optional.empty();
        if (!container.has(key.namespacedKey(), type))
            throw new IllegalStateException("Stored value at " + key + " has a different primitive type than " + type.getComplexType().getSimpleName());
        T value = container.get(key.namespacedKey(), type);
        if (value == null) return Optional.empty();
        try { return Optional.of(defensive(value)); }
        catch (RuntimeException exception) { throw new IllegalStateException("Could not decode persistent value at " + key, exception); }
    }

    public static <T> T getOrDefault(PersistentDataHolder holder, DataKey<T> key, T defaultValue) {
        return get(holder, key).orElse(Objects.requireNonNull(defaultValue, "defaultValue"));
    }
    public static <T> T getOrDefault(PersistentDataContainer container, DataKey<T> key, T defaultValue) {
        return get(container, key).orElse(Objects.requireNonNull(defaultValue, "defaultValue"));
    }

    public static <T> boolean has(PersistentDataHolder holder, DataKey<T> key) {
        return get(holder, key).isPresent();
    }

    public static void remove(PersistentDataHolder holder, DataKey<?> key) {
        Objects.requireNonNull(holder, "holder").getPersistentDataContainer().remove(Objects.requireNonNull(key, "key").namespacedKey());
    }
    public static void remove(PersistentDataContainer container, DataKey<?> key) {
        Objects.requireNonNull(container, "container").remove(Objects.requireNonNull(key, "key").namespacedKey());
    }

    /** Writes through ItemMeta and assigns the edited snapshot back to the supplied stack. */
    public static <T> ItemStack set(ItemStack item, DataKey<T> key, T value) {
        ItemStack target = Objects.requireNonNull(item, "item");
        ItemMeta meta = Objects.requireNonNull(target.getItemMeta(), "Item does not support metadata");
        set(meta.getPersistentDataContainer(), key, value);
        target.setItemMeta(meta);
        return target;
    }

    public static <T> Optional<T> get(ItemStack item, DataKey<T> key) {
        ItemMeta meta = Objects.requireNonNull(Objects.requireNonNull(item, "item").getItemMeta(), "Item does not support metadata");
        return get(meta.getPersistentDataContainer(), key);
    }

    public static void remove(ItemStack item, DataKey<?> key) {
        ItemStack target = Objects.requireNonNull(item, "item");
        ItemMeta meta = Objects.requireNonNull(target.getItemMeta(), "Item does not support metadata");
        meta.getPersistentDataContainer().remove(Objects.requireNonNull(key, "key").namespacedKey());
        target.setItemMeta(meta);
    }

    /** Commits PDC changes on a TileState snapshot by calling {@link TileState#update()}. */
    public static <T> boolean setAndUpdate(TileState state, DataKey<T> key, T value) {
        set((PersistentDataHolder) Objects.requireNonNull(state, "state"), key, value);
        return state.update();
    }

    private static <T> T defensive(T value) {
        if (value instanceof byte[] bytes) return (T) bytes.clone();
        if (value instanceof int[] ints) return (T) ints.clone();
        if (value instanceof long[] longs) return (T) longs.clone();
        return value;
    }
}
