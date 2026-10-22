package com.trickshotmlg.devkit.storage.migrations;

import com.trickshotmlg.devkit.storage.SqlParameters;
import com.trickshotmlg.devkit.storage.Storage;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mysql.MySQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Real Flyway compatibility checks; this class runs only under the explicit integrationTest task. */
@Testcontainers
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class NetworkMigrationIntegrationTest {
    private static final String USER = "devkit_migration_it";
    private static final String PASSWORD = "temporary-migration-secret";

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11")
            .withDatabaseName("devkit_migration_it")
            .withUsername(USER)
            .withPassword(PASSWORD)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*\\n", 1)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    @Container
    private static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:11.4.13")
            .withDatabaseName("devkit_migration_it")
            .withUsername(USER)
            .withPassword(PASSWORD)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*\\n", 1)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    @TempDir Path temporary;

    static Stream<Backend> backends() {
        return Stream.of(
                new Backend("MySQL", MYSQL::getHost, MYSQL::getFirstMappedPort, MYSQL::getDatabaseName, "mysql"),
                new Backend("MariaDB", MARIADB::getHost, MARIADB::getFirstMappedPort, MARIADB::getDatabaseName, "mariadb"));
    }

    @ParameterizedTest(name = "{0} applies packaged migrations, records versions, and validates checksums")
    @MethodSource("backends")
    void packagedMigrationsRunAndValidate(Backend backend) {
        try (Storage storage = backend.open()) {
            StorageMigrations.Builder migrations = StorageMigrations.builder(storage)
                    .locations("classpath:db/migration/integration/" + backend.location())
                    .schemaHistoryTable("history_" + backend.location());
            MigrationReport first = migrations.migrate();
            assertEquals(2, first.migrationsExecuted(), backend.name());
            assertEquals("2", first.currentVersion().orElseThrow(), backend.name());
            assertEquals(0, migrations.migrate().migrationsExecuted(), backend.name());
            migrations.validate();
            assertEquals(1L, storage.queryOne("SELECT balance FROM storage_it_accounts WHERE id = ?",
                    SqlParameters.of(1L), row -> row.getLong("balance")).orElseThrow(), backend.name());
            assertEquals("created by migration", storage.queryOne("SELECT note FROM storage_it_accounts WHERE id = ?",
                    SqlParameters.of(1L), row -> row.getString(1)).orElseThrow(), backend.name());
            assertEquals(2L, storage.queryOne("SELECT COUNT(*) AS total FROM history_" + backend.location(),
                    SqlParameters.none(), row -> row.getLong("total")).orElseThrow(), backend.name());
        }
    }

    @ParameterizedTest(name = "{0} rejects changed checksums and keeps the applied database available")
    @MethodSource("backends")
    void changedMigrationChecksumIsRejected(Backend backend) throws Exception {
        Path location = Files.createDirectories(temporary.resolve("migrations-" + backend.location()));
        Path migration = location.resolve("V1__create_checksum_table.sql");
        Files.writeString(migration, "CREATE TABLE checksum_probe(id BIGINT PRIMARY KEY);\n");
        try (Storage storage = backend.open()) {
            StorageMigrations.Builder migrations = StorageMigrations.builder(storage)
                    .locations("filesystem:" + location)
                    .schemaHistoryTable("checksum_" + backend.location());
            assertEquals(1, migrations.migrate().migrationsExecuted(), backend.name());
            Files.writeString(migration, "CREATE TABLE checksum_probe(id BIGINT PRIMARY KEY, note VARCHAR(32));\n");
            assertThrows(StorageMigrationException.class, migrations::validate, backend.name());
            assertEquals(0L, storage.queryOne("SELECT COUNT(*) AS total FROM checksum_probe", SqlParameters.none(),
                    row -> row.getLong("total")).orElseThrow(), backend.name());
        }
    }

    @ParameterizedTest(name = "{0} reports invalid migration SQL without reporting a successful version")
    @MethodSource("backends")
    void failedMigrationIsReportedAndDoesNotCreateTheTargetTable(Backend backend) throws Exception {
        Path location = Files.createDirectories(temporary.resolve("invalid-" + backend.location()));
        Files.writeString(location.resolve("V1__invalid.sql"), "THIS IS NOT VALID SQL;\n");
        try (Storage storage = backend.open()) {
            StorageMigrationException failure = assertThrows(StorageMigrationException.class,
                    () -> StorageMigrations.builder(storage).locations("filesystem:" + location)
                            .schemaHistoryTable("failed_" + backend.location()).migrate(), backend.name());
            assertNotNull(failure.getCause(), backend.name());
            assertEquals(0L, storage.queryOne("SELECT COUNT(*) AS total FROM failed_" + backend.location()
                            + " WHERE version = ? AND success = ?",
                    SqlParameters.of("1", true), row -> row.getLong("total")).orElseThrow(),
                    "failed migration must not be reported as successful on " + backend.name());
            assertEquals(1, storage.queryOne("SELECT 1 AS value", SqlParameters.none(), row -> row.getInt("value")).orElseThrow(),
                    "storage remains usable after migration failure");
        }
    }

    private record Backend(String name, Supplier<String> host, Supplier<Integer> port, Supplier<String> database,
                           String location) {
        Storage open() {
            StorageBuilderSupport builder = new StorageBuilderSupport(location);
            return builder.open(host.get(), port.get(), database.get(), USER, PASSWORD);
        }
        @Override public String toString() { return name; }
    }

    /** Keeps the test's backend selection explicit while using the public Storage builder. */
    private record StorageBuilderSupport(String location) {
        Storage open(String host, int port, String database, String username, String password) {
            var builder = Storage.builder();
            if (location.equals("mysql")) builder.mysql(host, port, database);
            else builder.mariadb(host, port, database);
            return builder.credentials(username, password).maximumPoolSize(4)
                    .connectionTimeout(Duration.ofSeconds(3)).build();
        }
    }
}
