package com.trickshotmlg.devkit.storage;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.JDBCType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Real JDBC integration checks. Invoked only by the explicit integrationTest task. */
@Testcontainers
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class NetworkDatabaseIntegrationTest {
    private static final String TEST_USER = "devkit_it";
    private static final String TEST_PASSWORD = "temporary-it-secret";

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11")
            .withDatabaseName("devkit_storage_it")
            .withUsername(TEST_USER)
            .withPassword(TEST_PASSWORD)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*\\n", 1)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    @Container
    private static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:11.4.13")
            .withDatabaseName("devkit_storage_it")
            .withUsername(TEST_USER)
            .withPassword(TEST_PASSWORD)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*\\n", 1)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    static Stream<Backend> backends() {
        return Stream.of(
                new Backend("MySQL", MYSQL::getHost, MYSQL::getFirstMappedPort, MYSQL::getDatabaseName,
                        "mysql", "MySQL Connector/J"),
                new Backend("MariaDB", MARIADB::getHost, MARIADB::getFirstMappedPort, MARIADB::getDatabaseName,
                        "mariadb", "MariaDB Connector/J"));
    }

    @ParameterizedTest(name = "{0} uses its JDBC driver and authenticated Hikari connection")
    @MethodSource("backends")
    void connectsWithExpectedDriverAndRedactsCredentials(Backend backend) throws Exception {
        try (Storage storage = backend.open(4, Duration.ofSeconds(3))) {
            assertEquals(1, storage.queryOne("SELECT 1 AS value", SqlParameters.none(), row -> row.getInt("value")).orElseThrow());
            try (var connection = storage.dataSource().getConnection()) {
                var metadata = connection.getMetaData();
                String productName = metadata.getDatabaseProductName();
                String driverName = metadata.getDriverName();
                assertTrue(productName.toLowerCase().contains(backend.productToken()),
                        backend.name() + " connected to unexpected product " + productName);
                assertTrue(driverName.contains(backend.driverName()),
                        backend.name() + " used unexpected JDBC driver " + driverName);
            }
            assertFalse(storage.configuration().toString().contains(TEST_PASSWORD));
            assertFalse(storage.toString().contains(TEST_PASSWORD));
        }
    }

    @ParameterizedTest(name = "{0} binds values, maps nullable and binary columns, and enforces queryOne cardinality")
    @MethodSource("backends")
    void preparedQueriesMapRowsAndKeepValuesAsData(Backend backend) {
        String table = tableName();
        byte[] payload = {1, 2, 3, 4};
        try (Storage storage = backend.open(4, Duration.ofSeconds(3))) {
            storage.update("CREATE TABLE " + table + " (id BIGINT PRIMARY KEY, name VARCHAR(255) NOT NULL, amount BIGINT NULL, payload BLOB NULL)", SqlParameters.none());
            String hostileLookingText = "O'Brien ' OR 1=1 -- \u6d4b\u8bd5";
            storage.update("INSERT INTO " + table + " (id, name, amount, payload) VALUES (?, ?, ?, ?)",
                    SqlParameters.of(1L, hostileLookingText, SqlParameter.nullOf(JDBCType.BIGINT), payload));
            payload[0] = 99;

            Value found = storage.queryOne("SELECT id, name, amount, payload FROM " + table + " WHERE name = ?",
                    SqlParameters.of(hostileLookingText), row -> new Value(row.getLong("id"), row.getString("name"),
                            row.getLongObject("amount"), row.getBytes("payload"))).orElseThrow();
            assertEquals(1L, found.id());
            assertEquals(hostileLookingText, found.name());
            assertNull(found.amount());
            assertArrayEquals(new byte[]{1, 2, 3, 4}, found.payload());
            assertTrue(storage.queryOne("SELECT id FROM " + table + " WHERE id = ?", SqlParameters.of(404L),
                    row -> row.getLong("id")).isEmpty());

            storage.update("INSERT INTO " + table + " (id, name, amount) VALUES (2, 'second', 9), (3, 'third', 10)",
                    SqlParameters.none());
            assertEquals(3, storage.queryList("SELECT id FROM " + table, SqlParameters.none(), row -> row.getLong("id")).size());
            assertThrows(StorageException.class, () -> storage.queryOne("SELECT id FROM " + table,
                    SqlParameters.none(), row -> row.getLong("id")));
        }
    }

    @ParameterizedTest(name = "{0} commits and rolls back transaction work")
    @MethodSource("backends")
    void transactionsCommitAtomicallyAndRollbackAfterCallbackFailure(Backend backend) {
        String table = tableName();
        try (Storage storage = backend.open(4, Duration.ofSeconds(3))) {
            storage.update("CREATE TABLE " + table + " (id BIGINT PRIMARY KEY, balance BIGINT NOT NULL) ENGINE=InnoDB", SqlParameters.none());
            long[] transactionConnections = new long[2];
            storage.transaction(tx -> {
                transactionConnections[0] = tx.queryOne("SELECT CONNECTION_ID() AS connection_id", SqlParameters.none(),
                        row -> row.getLong("connection_id")).orElseThrow();
                tx.update("INSERT INTO " + table + " VALUES (?, ?)", SqlParameters.of(1L, 100L));
                tx.update("INSERT INTO " + table + " VALUES (?, ?)", SqlParameters.of(2L, 25L));
                transactionConnections[1] = tx.queryOne("SELECT CONNECTION_ID() AS connection_id", SqlParameters.none(),
                        row -> row.getLong("connection_id")).orElseThrow();
                return null;
            });
            assertEquals(transactionConnections[0], transactionConnections[1],
                    "every transaction operation must share one physical server session");
            assertEquals(2, storage.queryList("SELECT id FROM " + table, SqlParameters.none(), row -> row.getLong("id")).size());

            IllegalStateException callbackFailure = new IllegalStateException("intentional rollback");
            StorageException observed = assertThrows(StorageException.class, () -> storage.transaction(tx -> {
                tx.update("UPDATE " + table + " SET balance = 0 WHERE id = 1", SqlParameters.none());
                throw callbackFailure;
            }));
            assertSame(callbackFailure, observed.getCause());
            assertEquals(100L, storage.queryOne("SELECT balance FROM " + table + " WHERE id = 1", SqlParameters.none(),
                    row -> row.getLong("balance")).orElseThrow());
            assertEquals(25L, storage.queryOne("SELECT balance FROM " + table + " WHERE id = 2", SqlParameters.none(),
                    row -> row.getLong("balance")).orElseThrow());

            assertThrows(StorageException.class, () -> storage.transaction(tx -> {
                tx.update("UPDATE " + table + " SET balance = 0 WHERE id = 1", SqlParameters.none());
                tx.update("INSERT INTO " + table + " VALUES (1, 5)", SqlParameters.none());
                return null;
            }));
            assertEquals(100L, storage.queryOne("SELECT balance FROM " + table + " WHERE id = 1", SqlParameters.none(),
                    row -> row.getLong("balance")).orElseThrow());
            assertEquals(1, storage.update("UPDATE " + table + " SET balance = balance + 1 WHERE id = 1", SqlParameters.none()));
            assertEquals(101L, storage.queryOne("SELECT balance FROM " + table + " WHERE id = 1", SqlParameters.none(),
                    row -> row.getLong("balance")).orElseThrow());
        }
    }

    @ParameterizedTest(name = "{0} executes bounded async work concurrently and recovers connections after pool exhaustion")
    @MethodSource("backends")
    void asyncWritesAreAtomicAndPoolRecoversAfterTimeout(Backend backend) throws Exception {
        String table = tableName();
        try (Storage storage = backend.open(4, Duration.ofSeconds(3))) {
            storage.update("CREATE TABLE " + table + " (id BIGINT PRIMARY KEY, value BIGINT NOT NULL) ENGINE=InnoDB", SqlParameters.none());
            storage.update("INSERT INTO " + table + " VALUES (1, 0)", SqlParameters.none());
            List<CompletableFuture<Integer>> increments = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                increments.add(storage.async().update("UPDATE " + table + " SET value = value + 1 WHERE id = 1", SqlParameters.none())
                        .result().toCompletableFuture());
            }
            CompletableFuture.allOf(increments.toArray(CompletableFuture[]::new)).get(20, TimeUnit.SECONDS);
            storage.async().transaction(tx -> tx.update("UPDATE " + table + " SET value = value + 3 WHERE id = 1", SqlParameters.none()))
                    .result().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(35L, storage.queryOne("SELECT value FROM " + table + " WHERE id = 1", SqlParameters.none(),
                    row -> row.getLong("value")).orElseThrow());

            String workerName = storage.async().submit(() -> Thread.currentThread().getName())
                    .result().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertNotEquals(Thread.currentThread().getName(), workerName);
        }

        try (Storage singleConnection = backend.open(1, Duration.ofMillis(500))) {
            try (var held = singleConnection.dataSource().getConnection()) {
                assertThrows(StorageException.class, () -> singleConnection.queryOne("SELECT 1 AS value", SqlParameters.none(), row -> row.getInt("value")));
            }
            assertEquals(1, singleConnection.queryOne("SELECT 1 AS value", SqlParameters.none(), row -> row.getInt("value")).orElseThrow());
        }
    }

    @ParameterizedTest(name = "{0} rejects invalid credentials without exposing the password")
    @MethodSource("backends")
    void authenticationFailureDoesNotExposeCredentials(Backend backend) {
        String secret = "wrong-auth-secret";
        StorageException failure = assertThrows(StorageException.class, () -> backend.openWithCredentials(
                "definitely_missing_user", secret, 1, Duration.ofSeconds(2)));
        for (Throwable current = failure; current != null; current = current.getCause()) {
            assertFalse(String.valueOf(current.getMessage()).contains(secret),
                    "credential leaked in " + current.getClass().getName());
        }
    }

    private static String tableName() { return "it_" + UUID.randomUUID().toString().replace("-", ""); }

    private record Value(long id, String name, Long amount, byte[] payload) { }

    private record Backend(String name, Supplier<String> host, Supplier<Integer> port, Supplier<String> database,
                           String productToken, String driverName) {
        Storage open(int poolSize, Duration connectionTimeout) {
            return builder(TEST_USER, TEST_PASSWORD, poolSize, connectionTimeout).build();
        }
        Storage openWithCredentials(String user, String password, int poolSize, Duration connectionTimeout) {
            return builder(user, password, poolSize, connectionTimeout).build();
        }
        private StorageBuilder builder(String user, String password, int poolSize, Duration connectionTimeout) {
            StorageBuilder builder = Storage.builder();
            if (productToken.equals("mysql")) builder.mysql(host.get(), port.get(), database.get());
            else builder.mariadb(host.get(), port.get(), database.get());
            return builder.credentials(user, password).maximumPoolSize(poolSize).connectionTimeout(connectionTimeout);
        }
        @Override public String toString() { return name; }
    }
}
