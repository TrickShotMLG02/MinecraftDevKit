package com.trickshotmlg.devkit.bukkit.data;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BukkitDataTest {
    private static final PersistentDataAdapterContext CONTEXT = (PersistentDataAdapterContext) Proxy.newProxyInstance(
            PersistentDataAdapterContext.class.getClassLoader(), new Class<?>[]{PersistentDataAdapterContext.class},
            (proxy, method, args) -> null);

    @Test void codecsRoundTripUuidBooleanAndStableEnumName() {
        UUID uuid = UUID.fromString("8abfdc8c-1b99-49a2-a79d-69a2cc75006c");
        var uuidType = DataCodec.uuid().persistentType();
        byte[] encoded = ((PersistentDataType<byte[], UUID>) uuidType).toPrimitive(uuid, CONTEXT);
        assertEquals(16, encoded.length);
        assertEquals(uuid, ((PersistentDataType<byte[], UUID>) uuidType).fromPrimitive(encoded, CONTEXT));
        var boolType = DataCodec.bool().persistentType();
        assertEquals(Boolean.TRUE, ((PersistentDataType<Byte, Boolean>) boolType).fromPrimitive((byte) 1, CONTEXT));
        assertThrows(IllegalArgumentException.class, () -> ((PersistentDataType<Byte, Boolean>) boolType).fromPrimitive((byte) 2, CONTEXT));
        var enumType = DataCodec.enumName(Mode.class).persistentType();
        assertEquals("ACTIVE", ((PersistentDataType<String, Mode>) enumType).toPrimitive(Mode.ACTIVE, CONTEXT));
        assertEquals(Mode.ACTIVE, ((PersistentDataType<String, Mode>) enumType).fromPrimitive("ACTIVE", CONTEXT));
        assertThrows(IllegalArgumentException.class, () -> ((PersistentDataType<String, Mode>) enumType).fromPrimitive("REMOVED", CONTEXT));
    }

    @Test void typedReadWriteDefaultRemovalAndArrayOwnership() {
        PersistentDataContainer container = container();
        DataKey<Integer> level = DataKey.integer(plugin(), "level");
        DataKey<byte[]> payload = DataKey.bytes(plugin(), "payload");
        assertEquals(0, BukkitData.getOrDefault(containerHolder(container), level, 0));
        BukkitData.set(container, level, 7);
        assertEquals(7, BukkitData.get(container, level).orElseThrow());
        byte[] input = {1, 2, 3};
        BukkitData.set(container, payload, input);
        input[0] = 99;
        byte[] read = BukkitData.get(container, payload).orElseThrow();
        assertArrayEquals(new byte[]{1, 2, 3}, read);
        read[1] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, BukkitData.get(container, payload).orElseThrow());
        BukkitData.remove(containerHolder(container), level);
        assertTrue(BukkitData.get(container, level).isEmpty());
    }

    @Test void wrongPrimitiveTypeFailsClearlyAndPathValidationIsStrict() {
        PersistentDataContainer container = container();
        DataKey<String> text = DataKey.string(plugin(), "value");
        DataKey<Integer> integer = DataKey.integer(plugin(), "value");
        BukkitData.set(container, text, "wrong type");
        assertThrows(IllegalStateException.class, () -> BukkitData.get(container, integer));
        assertThrows(IllegalArgumentException.class, () -> DataKey.string(plugin(), "UpperCase"));
        assertThrows(IllegalArgumentException.class, () -> DataKey.string(plugin(), "bad path"));
        assertNotEquals(DataKey.string(plugin(), "value"), DataKey.string(pluginNamed("other"), "value"));
    }

    private static PersistentDataHolderForTest containerHolder(PersistentDataContainer container) {
        return new PersistentDataHolderForTest(container);
    }
    private record PersistentDataHolderForTest(PersistentDataContainer getPersistentDataContainer) implements org.bukkit.persistence.PersistentDataHolder { }
    private static Plugin plugin() { return pluginNamed("MinecraftDevKitTest"); }
    private static Plugin pluginNamed(String name) {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> method.getName().equals("getName") ? name : null);
    }

    private static PersistentDataContainer container() {
        Map<NamespacedKey, Stored> values = new HashMap<>();
        return (PersistentDataContainer) Proxy.newProxyInstance(PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "set" -> { set(values, (NamespacedKey) args[0], (PersistentDataType<?, ?>) args[1], args[2]); yield null; }
                        case "get" -> get(values, (NamespacedKey) args[0], (PersistentDataType<?, ?>) args[1]);
                        case "has" -> args.length == 1 ? values.containsKey(args[0]) : has(values, (NamespacedKey) args[0], (PersistentDataType<?, ?>) args[1]);
                        case "remove" -> { values.remove(args[0]); yield null; }
                        case "getKeys" -> Set.copyOf(values.keySet());
                        case "isEmpty" -> values.isEmpty();
                        default -> throw new UnsupportedOperationException(method.toString());
                    };
                });
    }
    private static <P, C> void set(Map<NamespacedKey, Stored> values, NamespacedKey key, PersistentDataType<P, C> type, Object raw) {
        C complex = (C) raw;
        P primitive = type.toPrimitive(complex, CONTEXT);
        values.put(key, new Stored(type.getPrimitiveType(), primitive));
    }
    private static Object get(Map<NamespacedKey, Stored> values, NamespacedKey key, PersistentDataType<?, ?> type) {
        Stored stored = values.get(key);
        if (stored == null || stored.primitiveClass != type.getPrimitiveType()) return null;
        return decode(stored, type);
    }
    private static boolean has(Map<NamespacedKey, Stored> values, NamespacedKey key, PersistentDataType<?, ?> type) {
        Stored stored = values.get(key);
        return stored != null && stored.primitiveClass == type.getPrimitiveType();
    }
    private static <P, C> C decodeCaptured(Stored stored, PersistentDataType<P, C> type) { return type.fromPrimitive((P) stored.primitive, CONTEXT); }
    private static Object decode(Stored stored, PersistentDataType<?, ?> type) { return decodeCaptured(stored, (PersistentDataType) type); }
    private record Stored(Class<?> primitiveClass, Object primitive) { }
    private enum Mode { ACTIVE }
}
