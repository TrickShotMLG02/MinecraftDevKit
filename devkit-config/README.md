# devkit-config

`devkit-config` is the Java 17, platform-independent configuration model. It owns typed keys, reusable codecs, schema validation, immutable snapshots, atomic in-memory updates, dirty tracking, and change listeners. YAML, Bukkit, and Minecraft APIs are not dependencies of this module.

## Declare a schema

```java
public final class FriendConfig {
    public static final ConfigKey<Integer> MAX_FRIENDS = ConfigKey.integer("friends.max", 100)
        .range(1, 1000).description("Maximum friends per player").build();
    public static final ConfigKey<Boolean> REQUESTS_ENABLED =
        ConfigKey.bool("friends.requests.enabled", true).build();
    public static final ConfigKey<Duration> REQUEST_TIMEOUT =
        ConfigKey.duration("friends.requests.timeout", Duration.ofMinutes(5))
            .validate(value -> value.isNegative() || value.isZero()
                ? Optional.of("must be positive") : Optional.empty()).build();

    public static final ConfigSchema SCHEMA = ConfigSchema.builder().version(2)
        .register(MAX_FRIENDS, REQUESTS_ENABLED, REQUEST_TIMEOUT)
        .validate(values -> values.get(REQUESTS_ENABLED) && values.get(REQUEST_TIMEOUT).isNegative()
            ? List.of(ConfigIssue.error(REQUEST_TIMEOUT.path(), "must be positive when requests are enabled"))
            : List.of())
        .build();
}
```

`ConfigKey` has factories for strings, booleans, integer/long/float/double values, enums, UUIDs, and ISO-8601 `Duration`. `ConfigCodecs.list(codec)` and `ConfigCodecs.map(codec)` create immutable typed collections. `ConfigCodecs.of(...)` creates custom codecs. Enum names use exact, case-sensitive Java enum names. Numeric codecs reject overflow and fractional integer inputs. Null is rejected for all regular keys; represent absence with a key default or an explicit custom optional-value codec.

Keys are registered directly in Java. Registration is explicit rather than reflective. Paths use dot-separated identifiers; a path cannot be both a scalar and a parent section. `config-version` is reserved. Missing keys with defaults receive those values; missing keys without defaults are validation errors. Invalid values are never silently replaced by defaults. Sensitive keys redact codec/validator failure details.

## Read and update

```java
int maximum = store.get(FriendConfig.MAX_FRIENDS);
ConfigSnapshot stableView = store.snapshot();

store.update(transaction -> {
    transaction.set(FriendConfig.MAX_FRIENDS, 250);
    transaction.set(FriendConfig.REQUESTS_ENABLED, true);
});

boolean needsSave = store.isDirty();
AutoCloseable subscription = store.onChange(FriendConfig.MAX_FRIENDS, change -> {
    int oldValue = change.oldValue();
    int newValue = change.newValue();
});
```

Updates validate all values and cross-field constraints against a complete candidate. Any error rejects the entire transaction. A snapshot is immutable and continues to represent the generation from which it was obtained. Listener callbacks run synchronously on the publishing thread, after publication and outside the update lock. Listener failures are isolated through the store's configurable failure handler. Close the returned subscription when it is no longer needed.

`ConfigStore.defaults(schema)` creates an in-memory store from defaults. YAML adapters use `ConfigStore.fromValues(schema, decodedValues)`. Successful source reloads replace the full candidate atomically. Multiple stores can be created for independent files.

Dirty state is generation based: a programmatic change stays dirty until successful persistence marks that generation saved. A concurrent update during save remains dirty.
