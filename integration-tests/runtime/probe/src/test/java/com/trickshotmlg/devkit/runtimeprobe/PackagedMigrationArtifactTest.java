package com.trickshotmlg.devkit.runtimeprobe;

import com.trickshotmlg.devkit.storage.Storage;
import com.trickshotmlg.devkit.storage.migrations.StorageMigrations;
import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class PackagedMigrationArtifactTest {
    private static final String MIGRATION = "db/migration/runtime/V1__create_runtime_probe.sql";

    @Test void shadedProbeJarMigrationsAreDiscoveredWithoutContextLoader() throws Exception {
        Path jar = Path.of(System.getProperty("mdk.plugin.jar"));
        assertTrue(Files.isRegularFile(jar), "Expected built shaded probe artifact at " + jar);
        try (ZipFile archive = new ZipFile(jar.toFile())) {
            assertNotNull(archive.getEntry(MIGRATION), "Migration must be present in the actual shaded plugin JAR");
        }

        Path database = Files.createTempFile("mdk-probe-packaged-", ".sqlite");
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader pluginLoader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, getClass().getClassLoader());
             Storage storage = Storage.sqlite(database)) {
            assertNotNull(pluginLoader.getResource(MIGRATION), "Plugin-style classloader must expose archived SQL resource");
            Thread.currentThread().setContextClassLoader(ClassLoader.getPlatformClassLoader());
            var first = StorageMigrations.builder(storage).classLoader(pluginLoader)
                    .locations("classpath:db/migration/runtime").migrate();
            assertEquals(1, first.migrationsExecuted());
            assertEquals("1", first.currentVersion().orElseThrow());
            assertEquals("migration-ready", storage.queryOne("SELECT value FROM runtime_probe WHERE id = ?",
                    com.trickshotmlg.devkit.storage.SqlParameters.of(1), row -> row.getString("value")).orElseThrow());

            var repeated = StorageMigrations.builder(storage).classLoader(pluginLoader)
                    .locations("classpath:db/migration/runtime").migrate();
            assertEquals(0, repeated.migrationsExecuted());
            assertEquals("1", repeated.currentVersion().orElseThrow());
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            Files.deleteIfExists(database);
        }
    }
}
