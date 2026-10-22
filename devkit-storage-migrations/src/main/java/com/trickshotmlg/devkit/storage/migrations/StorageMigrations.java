package com.trickshotmlg.devkit.storage.migrations;

import com.trickshotmlg.devkit.storage.Storage;
import org.flywaydb.core.Flyway;

import java.util.Arrays;
import java.util.Objects;

/** Optional Flyway integration. The supplied Storage retains ownership of its DataSource. */
public final class StorageMigrations {
    private StorageMigrations() { }

    public static Builder builder(Storage storage) { return new Builder(storage); }

    public static final class Builder {
        private final Storage storage;
        private String[] locations = {"classpath:db/migration"};
        private String schemaHistoryTable = "flyway_schema_history";

        private Builder(Storage storage) { this.storage = Objects.requireNonNull(storage, "storage"); }

        /** Sets Flyway locations, such as {@code classpath:db/migration/sqlite}. */
        public Builder locations(String... locations) {
            if (locations == null || locations.length == 0 || Arrays.stream(locations).anyMatch(String::isBlank))
                throw new IllegalArgumentException("At least one non-empty migration location is required");
            this.locations = locations.clone();
            return this;
        }

        public Builder schemaHistoryTable(String table) {
            if (table == null || !table.matches("[A-Za-z_][A-Za-z0-9_]*"))
                throw new IllegalArgumentException("Invalid schema history table name");
            schemaHistoryTable = table;
            return this;
        }

        /** Validates applied migration checksums without applying pending migrations. */
        public void validate() {
            try { flyway().validate(); }
            catch (RuntimeException failure) { throw new StorageMigrationException("Database migration validation failed", failure); }
        }

        /** Validates applied migrations, applies pending migrations, and returns an immutable summary. */
        public MigrationReport migrate() {
            try {
                var result = flyway().migrate();
                return new MigrationReport(result.migrationsExecuted,
                        result.targetSchemaVersion == null ? java.util.Optional.empty()
                                : java.util.Optional.of(result.targetSchemaVersion.toString()));
            } catch (RuntimeException failure) {
                throw new StorageMigrationException("Database migrations failed", failure);
            }
        }

        private Flyway flyway() {
            return Flyway.configure()
                    .dataSource(storage.dataSource())
                    .locations(locations)
                    .table(schemaHistoryTable)
                    .cleanDisabled(true)
                    .baselineOnMigrate(false)
                    .load();
        }
    }
}
