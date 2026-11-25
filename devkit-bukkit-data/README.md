# devkit-bukkit-data

Typed helpers for Bukkit `PersistentDataContainer`, compiled against Spigot API 1.21.11.

```java
DataKey<Integer> LEVEL = DataKey.integer(plugin, "level");
DataKey<UUID> OWNER = DataKey.uuid(plugin, "owner");
BukkitData.set(player, LEVEL, 15);
int level = BukkitData.getOrDefault(player, LEVEL, 0);
BukkitData.remove(player, OWNER);
```

Built-in scalar/array types mirror Bukkit types. UUID is encoded as two big-endian longs (16 bytes), booleans as byte 0/1, and enums by constant name. Unknown enum names and wrongly typed existing keys fail explicitly. Arrays are copied on write/read. `DataCodec.of(PersistentDataType)` supports application-defined deterministic codecs; it does not serialize arbitrary objects.

`BukkitData.set(ItemStack, ...)` and `remove(ItemStack, ...)` edit the ItemMeta snapshot and write it back to the supplied stack. For block entities, `set(TileState, ...)` only changes the snapshot; use `setAndUpdate(TileState, ...)` to commit via `BlockState.update()`. Entity/world/chunk persistence follows Bukkit's own save lifecycle. Perform mutation on a thread safe for the holder; this utility does not schedule or persist holders itself.
