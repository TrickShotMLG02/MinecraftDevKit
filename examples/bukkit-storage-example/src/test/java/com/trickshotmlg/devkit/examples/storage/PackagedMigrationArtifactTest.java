package com.trickshotmlg.devkit.examples.storage;

import com.trickshotmlg.devkit.storage.Storage;
import com.trickshotmlg.devkit.storage.SqlParameters;
import com.trickshotmlg.devkit.storage.migrations.StorageMigrations;
import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class PackagedMigrationArtifactTest {
    private static final String MIGRATION = "db/migration/V1__create_players.sql";

    @Test void shadedStorageJarMigrationRunsWithPluginLoaderAndContextLoaderBlind() throws Exception {
        Path jar = Path.of(System.getProperty("mdk.plugin.jar"));
        assertTrue(Files.isRegularFile(jar), "Expected built shaded storage artifact at " + jar);
        try (ZipFile archive = new ZipFile(jar.toFile())) {
            assertNotNull(archive.getEntry(MIGRATION), "Migration must be present in the actual shaded plugin JAR");
        }

        Path database = Files.createTempFile("mdk-storage-packaged-", ".sqlite");
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader pluginLoader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, getClass().getClassLoader());
             Storage storage = Storage.sqlite(database)) {
            assertNotNull(pluginLoader.getResource(MIGRATION));
            Thread.currentThread().setContextClassLoader(ClassLoader.getPlatformClassLoader());
            var first = StorageMigrations.builder(storage).classLoader(pluginLoader).locations("classpath:db/migration").migrate();
            assertEquals(1, first.migrationsExecuted());
            assertEquals("1", first.currentVersion().orElseThrow());
            assertTrue(storage.queryOne("SELECT name FROM sqlite_master WHERE type = ? AND name = ?",
                    SqlParameters.of("table", "players"), row -> row.getString("name")).isPresent());

            var repeated = StorageMigrations.builder(storage).classLoader(pluginLoader).locations("classpath:db/migration").migrate();
            assertEquals(0, repeated.migrationsExecuted());
            assertEquals("1", repeated.currentVersion().orElseThrow());

            Path emptyDatabase = Files.createTempFile("mdk-storage-missing-migration-", ".sqlite");
            try (Storage empty = Storage.sqlite(emptyDatabase)) {
                var missing = StorageMigrations.builder(empty).classLoader(pluginLoader)
                        .locations("classpath:db/migration/required-but-absent").migrate();
                assertTrue(missing.currentVersion().isEmpty(), "The storage example's required-V1 startup check must reject this absent version");
            } finally { Files.deleteIfExists(emptyDatabase); }
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            Files.deleteIfExists(database);
        }
    }
}
