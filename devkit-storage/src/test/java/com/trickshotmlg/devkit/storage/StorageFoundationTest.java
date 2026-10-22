package com.trickshotmlg.devkit.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class StorageFoundationTest {
    @TempDir Path temporary;

    @Test void sqliteCreatesParentPathAndEnablesForeignKeys() throws Exception {
        Path database = temporary.resolve("nested/data.db");
        try (Storage storage = Storage.sqlite(database)) {
            assertTrue(database.getParent().toFile().isDirectory());
            try (var connection = storage.dataSource().getConnection(); var statement = connection.createStatement();
                 var result = statement.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }
    }

    @Test void sqliteUsesOneConnectionForInMemoryDatabase() throws Exception {
        try (Storage storage = Storage.sqliteMemory()) {
            try (var connection = storage.dataSource().getConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE sample(value INTEGER)");
            }
            try (var connection = storage.dataSource().getConnection(); var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT count(*) FROM sample")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
    }

    @Test void externalDataSourceRemainsCallerOwned() throws Exception {
        SQLiteDataSource external = new SQLiteDataSource();
        external.setUrl("jdbc:sqlite::memory:");
        Storage storage = Storage.builder().dataSource(external).build();
        assertTrue(storage.configuration().toString().contains("<external-data-source>"));
        storage.close();
        storage.close();
        try (var connection = external.getConnection()) {
            assertFalse(connection.isClosed());
        }
    }

    @Test void configurationValidationAndDiagnosticsDoNotExposeCredentials() {
        char[] password = "very-secret".toCharArray();
        StorageConfiguration configuration = new StorageConfiguration("jdbc:mysql://localhost:3306/game", "user",
                password, 8, Duration.ofSeconds(10), Duration.ofMinutes(5), null, false, false, false);
        assertFalse(configuration.toString().contains("very-secret"));
        assertThrows(IllegalArgumentException.class, () -> new StorageConfiguration("jdbc:mysql://localhost/game",
                "user", password, 0, Duration.ofSeconds(10), Duration.ofMinutes(5), null, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> Storage.builder().connectionTimeout(Duration.ofMillis(-1))
                .mysql("localhost", 3306, "game").build());
        assertThrows(IllegalArgumentException.class, () -> Storage.builder().mysql("host?ssl=false", 3306, "game"));
    }
}
