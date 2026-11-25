package com.trickshotmlg.devkit.bukkit.data;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

/** Immutable typed key for Bukkit persistent data. */
public final class DataKey<T> {
    private final NamespacedKey key;
    private final DataCodec<T> codec;

    private DataKey(NamespacedKey key, DataCodec<T> codec) {
        this.key = Objects.requireNonNull(key, "key");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public static <T> DataKey<T> of(Plugin plugin, String path, DataCodec<T> codec) {
        Objects.requireNonNull(plugin, "plugin");
        validatePath(path);
        return new DataKey<>(new NamespacedKey(plugin, path), codec);
    }
    public static <T> DataKey<T> of(NamespacedKey key, DataCodec<T> codec) {
        return new DataKey<>(Objects.requireNonNull(key, "key"), codec);
    }

    public static DataKey<Byte> byteKey(Plugin plugin, String path) { return of(plugin, path, DataCodec.of(PersistentDataType.BYTE)); }
    public static DataKey<Short> shortKey(Plugin plugin, String path) { return of(plugin, path, DataCodec.of(PersistentDataType.SHORT)); }
    public static DataKey<Integer> integer(Plugin plugin, String path) { return of(plugin, path, DataCodec.of(PersistentDataType.INTEGER)); }
    public static DataKey<Long> longKey(Plugin plugin, String path) { return of(plugin, path, DataCodec.of(PersistentDataType.LONG)); }
    public static DataKey<Float> floatKey(Plugin plugin, String path) { return of(plugin, path, DataCodec.of(PersistentDataType.FLOAT)); }
    public static DataKey<Double> doubleKey(Plugin plugin, String path) { return of(plugin, path, DataCodec.of(PersistentDataType.DOUBLE)); }
    public static DataKey<String> string(Plugin plugin, String path) { return of(plugin, path, DataCodec.of(PersistentDataType.STRING)); }
    public static DataKey<byte[]> bytes(Plugin plugin, String path) { return of(plugin, path, DataCodec.of(PersistentDataType.BYTE_ARRAY)); }
    public static DataKey<int[]> integers(Plugin plugin, String path) { return of(plugin, path, DataCodec.of(PersistentDataType.INTEGER_ARRAY)); }
    public static DataKey<long[]> longs(Plugin plugin, String path) { return of(plugin, path, DataCodec.of(PersistentDataType.LONG_ARRAY)); }
    public static DataKey<java.util.UUID> uuid(Plugin plugin, String path) { return of(plugin, path, DataCodec.uuid()); }
    public static DataKey<Boolean> bool(Plugin plugin, String path) { return of(plugin, path, DataCodec.bool()); }
    public static <E extends Enum<E>> DataKey<E> enumName(Plugin plugin, String path, Class<E> type) {
        return of(plugin, path, DataCodec.enumName(type));
    }

    public NamespacedKey namespacedKey() { return key; }
    DataCodec<T> codec() { return codec; }

    private static void validatePath(String path) {
        Objects.requireNonNull(path, "path");
        if (path.isBlank() || !path.matches("[a-z0-9._/-]+") || path.startsWith("/") || path.endsWith("/") || path.contains("//"))
            throw new IllegalArgumentException("Invalid persistent data path: " + path);
    }

    @Override public boolean equals(Object other) { return other instanceof DataKey<?> dataKey && key.equals(dataKey.key); }
    @Override public int hashCode() { return key.hashCode(); }
    @Override public String toString() { return key.toString(); }
}
