package com.trickshotmlg.devkit.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable, ordered collection of typed configuration keys. */
public final class ConfigSchema {
    private final Map<String, ConfigKey<?>> keys;
    private final int version;

    private ConfigSchema(Builder builder) {
        LinkedHashMap<String, ConfigKey<?>> copy = new LinkedHashMap<>();
        builder.keys.values().stream().sorted(java.util.Comparator.comparing(ConfigKey::path))
                .forEach(key -> copy.put(key.path(), key));
        validatePathCollisions(copy.keySet());
        keys = Collections.unmodifiableMap(copy);
        version = builder.version;
    }

    public static Builder builder() { return new Builder(); }
    public Map<String, ConfigKey<?>> keys() { return keys; }
    public Optional<ConfigKey<?>> key(String path) { return Optional.ofNullable(keys.get(path)); }
    public int version() { return version; }

    /** Mutable schema builder used only during definition. */
    public static final class Builder {
        private final Map<String, ConfigKey<?>> keys = new LinkedHashMap<>();
        private int version = 1;
        public Builder register(ConfigKey<?> key) {
            Objects.requireNonNull(key, "key");
            ConfigKey<?> existing = keys.putIfAbsent(key.path(), key);
            if (existing != null) throw new IllegalArgumentException("Duplicate configuration key path '" + key.path() + "'");
            return this;
        }
        public Builder register(ConfigKey<?>... keys) { for (ConfigKey<?> key : keys) register(key); return this; }
        /** Sets the persisted configuration version. Version 1 is the default. */
        public Builder version(int version) {
            if (version < 1) throw new IllegalArgumentException("schema version must be at least 1");
            this.version = version;
            return this;
        }
        public ConfigSchema build() { return new ConfigSchema(this); }
    }

    private static void validatePathCollisions(java.util.Set<String> paths) {
        for (String path : paths) {
            int index = path.indexOf('.');
            while (index >= 0) {
                String prefix = path.substring(0, index);
                if (paths.contains(prefix)) throw new IllegalArgumentException("Configuration path '" + prefix + "' cannot be both a value and a section (conflicts with '" + path + "')");
                index = path.indexOf('.', index + 1);
            }
        }
    }
}
