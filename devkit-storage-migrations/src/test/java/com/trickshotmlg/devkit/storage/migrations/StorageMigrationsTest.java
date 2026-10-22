package com.trickshotmlg.devkit.storage.migrations;

import com.trickshotmlg.devkit.storage.Storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StorageMigrationsTest {
    @TempDir Path temporary;

    @Test void packagedMigrationsApplyOnceAndCreateExpectedSchema() throws Exception {
        try (Storage storage = Storage.sqlite(temporary.resolve("migration.db"))) {
            StorageMigrations.Builder migrations = StorageMigrations.builder(storage)
                    .locations("classpath:db/migration/test");
            MigrationReport first = migrations.migrate();
            assertEquals(2, first.migrationsExecuted());
            assertEquals("2", first.currentVersion().orElseThrow());
            assertEquals(0, migrations.migrate().migrationsExecuted());
            assertEquals(2, storage.queryOne("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name IN ('players', 'player_settings')",
                    com.trickshotmlg.devkit.storage.SqlParameters.none(), row -> row.getInt(1)).orElseThrow());
            migrations.validate();
        }
    }

    @Test void validationRejectsChangedAppliedMigration() throws Exception {
        Path location = Files.createDirectories(temporary.resolve("migrations"));
        Path migration = location.resolve("V1__create_items.sql");
        Files.writeString(migration, "CREATE TABLE items(id INTEGER PRIMARY KEY);\n");
        try (Storage storage = Storage.sqlite(temporary.resolve("checksum.db"))) {
            StorageMigrations.Builder runner = StorageMigrations.builder(storage).locations("filesystem:" + location);
            assertEquals(1, runner.migrate().migrationsExecuted());
            Files.writeString(migration, "CREATE TABLE items(id INTEGER PRIMARY KEY, name TEXT);\n");
            assertThrows(StorageMigrationException.class, runner::validate);
        }
    }

    @Test void invalidSqlFailsStartupWithoutClaimingMigrationSuccess() throws Exception {
        Path location = Files.createDirectories(temporary.resolve("bad"));
        Files.writeString(location.resolve("V1__invalid.sql"), "THIS IS NOT VALID SQL;\n");
        try (Storage storage = Storage.sqlite(temporary.resolve("invalid.db"))) {
            StorageMigrationException failure = assertThrows(StorageMigrationException.class,
                    () -> StorageMigrations.builder(storage).locations("filesystem:" + location).migrate());
            assertNotNull(failure.getCause());
            assertEquals(0, storage.queryOne("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'items'",
                    com.trickshotmlg.devkit.storage.SqlParameters.none(), row -> row.getInt(1)).orElseThrow());
        }
    }

    @Test void migrationLocationsAreValidatedAndNoDestructiveCleanIsExposed() throws Exception {
        try (Storage storage = Storage.sqlite(temporary.resolve("options.db"))) {
            assertThrows(IllegalArgumentException.class, () -> StorageMigrations.builder(storage).locations());
            assertThrows(IllegalArgumentException.class,
                    () -> StorageMigrations.builder(storage).schemaHistoryTable("history; DROP TABLE users"));
        }
    }
}
