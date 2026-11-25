package com.trickshotmlg.devkit.bukkit.data;

import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.UUID;

/** A typed, deterministic mapping between an application value and a Bukkit PDC representation. */
public final class DataCodec<T> {
    private final PersistentDataType<?, T> type;

    private DataCodec(PersistentDataType<?, T> type) { this.type = Objects.requireNonNull(type, "type"); }

    /** Wraps a Bukkit-supported persistent data type. */
    public static <P, T> DataCodec<T> of(PersistentDataType<P, T> type) { return new DataCodec<>(type); }

    /** Stores UUIDs as exactly 16 bytes in most-significant/least-significant order. */
    public static DataCodec<UUID> uuid() {
        return of(new PersistentDataType<byte[], UUID>() {
            @Override public Class<byte[]> getPrimitiveType() { return byte[].class; }
            @Override public Class<UUID> getComplexType() { return UUID.class; }
            @Override public byte[] toPrimitive(UUID value, org.bukkit.persistence.PersistentDataAdapterContext context) {
                Objects.requireNonNull(value, "value");
                return java.nio.ByteBuffer.allocate(16).putLong(value.getMostSignificantBits())
                        .putLong(value.getLeastSignificantBits()).array();
            }
            @Override public UUID fromPrimitive(byte[] bytes, org.bukkit.persistence.PersistentDataAdapterContext context) {
                if (bytes == null || bytes.length != 16) throw new IllegalArgumentException("UUID PDC value must contain exactly 16 bytes");
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
                return new UUID(buffer.getLong(), buffer.getLong());
            }
        });
    }

    /** Stores booleans as byte 0 or 1; other stored byte values are rejected when decoded. */
    public static DataCodec<Boolean> bool() {
        return of(new PersistentDataType<Byte, Boolean>() {
            @Override public Class<Byte> getPrimitiveType() { return Byte.class; }
            @Override public Class<Boolean> getComplexType() { return Boolean.class; }
            @Override public Byte toPrimitive(Boolean value, org.bukkit.persistence.PersistentDataAdapterContext context) {
                return (byte) (Objects.requireNonNull(value, "value") ? 1 : 0);
            }
            @Override public Boolean fromPrimitive(Byte value, org.bukkit.persistence.PersistentDataAdapterContext context) {
                if (value == null || (value != 0 && value != 1)) throw new IllegalArgumentException("Boolean PDC value must be 0 or 1");
                return value == 1;
            }
        });
    }

    /** Stores enum constants by stable name, never by ordinal. Removed/renamed constants fail on read. */
    public static <E extends Enum<E>> DataCodec<E> enumName(Class<E> enumType) {
        Objects.requireNonNull(enumType, "enumType");
        return of(new PersistentDataType<String, E>() {
            @Override public Class<String> getPrimitiveType() { return String.class; }
            @Override public Class<E> getComplexType() { return enumType; }
            @Override public String toPrimitive(E value, org.bukkit.persistence.PersistentDataAdapterContext context) {
                return Objects.requireNonNull(value, "value").name();
            }
            @Override public E fromPrimitive(String value, org.bukkit.persistence.PersistentDataAdapterContext context) {
                try { return Enum.valueOf(enumType, Objects.requireNonNull(value, "value")); }
                catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Unknown " + enumType.getSimpleName() + " PDC value: " + value, exception);
                }
            }
        });
    }

    PersistentDataType<?, T> persistentType() { return type; }
}
