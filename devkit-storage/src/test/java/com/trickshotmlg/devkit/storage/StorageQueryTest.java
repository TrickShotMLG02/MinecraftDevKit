package com.trickshotmlg.devkit.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.JDBCType;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StorageQueryTest {
    @TempDir Path temporary;

    @Test void bindsUntrustedStringsAndMapsTypedRows() {
        try (Storage storage = Storage.sqlite(temporary.resolve("queries.db"))) {
            storage.update("CREATE TABLE player_data(name TEXT PRIMARY KEY, coins INTEGER, uuid TEXT, joined TEXT, payload BLOB)", SqlParameters.none());
            String injection = "x'); DROP TABLE player_data; --";
            UUID uuid = UUID.randomUUID();
            Instant joined = Instant.parse("2025-01-02T03:04:05Z");
            byte[] bytes = {1, 2, 3};
            SqlParameters parameters = SqlParameters.of(injection, 50L, uuid, joined, bytes);
            bytes[0] = 9;
            storage.update("INSERT INTO player_data(name, coins, uuid, joined, payload) VALUES (?, ?, ?, ?, ?)", parameters);

            var found = storage.queryOne("SELECT name, coins, uuid, joined, payload FROM player_data WHERE name = ?",
                    SqlParameters.of(injection), row -> new PlayerRow(row.getString("name"), row.getLong("coins"),
                            row.getUuid("uuid"), row.getInstant("joined"), row.getBytes("payload")));
            assertTrue(found.isPresent());
            assertEquals(injection, found.get().name());
            assertEquals(50, found.get().coins());
            assertEquals(uuid, found.get().uuid());
            assertEquals(joined, found.get().joined());
            assertArrayEquals(new byte[]{1, 2, 3}, found.get().payload());
            assertEquals(1, storage.queryList("SELECT name FROM player_data", SqlParameters.none(), row -> row.getString(1)).size());
        }
    }

    @Test void queryOneDistinguishesEmptyAndRejectsMultipleRows() {
        try (Storage storage = Storage.sqliteMemory()) {
            storage.update("CREATE TABLE values_table(value INTEGER)", SqlParameters.none());
            assertTrue(storage.queryOne("SELECT value FROM values_table", SqlParameters.none(), row -> row.getInteger("value")).isEmpty());
            storage.update("INSERT INTO values_table VALUES (1), (2)", SqlParameters.none());
            assertThrows(StorageException.class, () -> storage.queryOne("SELECT value FROM values_table",
                    SqlParameters.none(), row -> row.getInt("value")));
            assertEquals(2, storage.queryList("SELECT value FROM values_table ORDER BY value",
                    SqlParameters.none(), row -> row.getInt("value")).size());
        }
    }

    @Test void typedSqlNullAndPrimitiveNullStateRemainObservable() {
        try (Storage storage = Storage.sqliteMemory()) {
            storage.update("CREATE TABLE nullable_values(value INTEGER)", SqlParameters.none());
            storage.update("INSERT INTO nullable_values VALUES (?)", SqlParameters.of(SqlParameter.nullOf(JDBCType.INTEGER)));
            var value = storage.queryOne("SELECT value FROM nullable_values", SqlParameters.none(), row -> {
                Integer nullable = row.getInteger("value");
                int primitive = row.getInt("value");
                return new NullObservation(nullable, primitive, row.wasNull());
            }).orElseThrow();
            assertNull(value.nullable());
            assertEquals(0, value.primitive());
            assertTrue(value.wasNull());
        }
    }

    @Test void mapperFailureClosesConnectionAndRowCannotEscapeMapper() {
        try (Storage storage = Storage.sqliteMemory()) {
            storage.update("CREATE TABLE mapped(value TEXT)", SqlParameters.none());
            storage.update("INSERT INTO mapped VALUES ('value')", SqlParameters.none());
            Row[] escaped = new Row[1];
            assertThrows(StorageException.class, () -> storage.queryOne("SELECT value FROM mapped", SqlParameters.none(), row -> {
                escaped[0] = row;
                throw new IllegalStateException("mapping failed");
            }));
            assertThrows(IllegalStateException.class, () -> escaped[0].getString("value"));
            assertEquals("value", storage.queryOne("SELECT value FROM mapped", SqlParameters.none(), row -> row.getString(1)).orElseThrow());
        }
    }

    @Test void parametersRejectUnknownTypesAndRedactValues() {
        assertThrows(IllegalArgumentException.class, () -> SqlParameters.of(new Object()));
        assertFalse(SqlParameters.of("do-not-log-me", 12).toString().contains("do-not-log-me"));
        assertTrue(SqlParameters.of("anything").toString().contains("count=1"));
    }

    private record PlayerRow(String name, long coins, UUID uuid, Instant joined, byte[] payload) { }
    private record NullObservation(Integer nullable, int primitive, boolean wasNull) { }
}
