package com.trickshotmlg.devkit.config.yaml;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable ordered one-version-at-a-time configuration migration registry. */
public final class ConfigMigrationRegistry {
    private final Map<Integer, ConfigMigration> migrations;
    private ConfigMigrationRegistry(Map<Integer, ConfigMigration> migrations) {
        this.migrations = Collections.unmodifiableMap(new TreeMap<>(migrations));
    }
    public static Builder builder() { return new Builder(); }
    ConfigMigration migrationFrom(int version) { return migrations.get(version); }

    public static final class Builder {
        private final Map<Integer, ConfigMigration> migrations = new TreeMap<>();
        /** Registers a single contiguous migration from {@code fromVersion} to {@code fromVersion + 1}. */
        public Builder register(int fromVersion, int toVersion, ConfigMigration migration) {
            if (fromVersion < 1 || toVersion != fromVersion + 1) throw new IllegalArgumentException("Migrations must advance exactly one positive schema version");
            if (migrations.putIfAbsent(fromVersion, Objects.requireNonNull(migration, "migration")) != null)
                throw new IllegalArgumentException("Duplicate migration from version " + fromVersion);
            return this;
        }
        public ConfigMigrationRegistry build() { return new ConfigMigrationRegistry(migrations); }
    }
}
