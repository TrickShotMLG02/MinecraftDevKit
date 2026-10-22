package com.trickshotmlg.devkit.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class StorageTransactionTest {
    @TempDir Path temporary;

    @Test void transactionCommitsAllStatementsOnOneConnection() {
        try (Storage storage = Storage.sqlite(temporary.resolve("commit.db"))) {
            storage.update("CREATE TABLE accounts(id INTEGER PRIMARY KEY, balance INTEGER NOT NULL)", SqlParameters.none());
            storage.transaction(transaction -> {
                transaction.update("INSERT INTO accounts VALUES (?, ?)", SqlParameters.of(1, 100));
                transaction.update("INSERT INTO accounts VALUES (?, ?)", SqlParameters.of(2, 20));
                transaction.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", SqlParameters.of(30, 1));
                transaction.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", SqlParameters.of(30, 2));
                return null;
            });
            List<Integer> balances = storage.queryList("SELECT balance FROM accounts ORDER BY id", SqlParameters.none(), row -> row.getInt(1));
            assertEquals(List.of(70, 50), balances);
        }
    }

    @Test void callbackFailureRollsBackAndConnectionIsReusable() {
        try (Storage storage = Storage.sqlite(temporary.resolve("rollback.db"))) {
            storage.update("CREATE TABLE values_table(value INTEGER)", SqlParameters.none());
            IllegalStateException original = new IllegalStateException("failure");
            StorageException failure = assertThrows(StorageException.class, () -> storage.transaction(transaction -> {
                transaction.update("INSERT INTO values_table VALUES (1)", SqlParameters.none());
                throw original;
            }));
            assertSame(original, failure.getCause());
            assertEquals(0, storage.queryList("SELECT value FROM values_table", SqlParameters.none(), row -> row.getInt(1)).size());
            assertEquals(1, storage.update("INSERT INTO values_table VALUES (2)", SqlParameters.none()));
        }
    }

    @Test void transactionIsThreadConfinedNonEscapingAndRejectsNesting() throws Exception {
        try (Storage storage = Storage.sqliteMemory()) {
            storage.update("CREATE TABLE values_table(value INTEGER)", SqlParameters.none());
            Transaction[] escaped = new Transaction[1];
            storage.transaction(transaction -> {
                escaped[0] = transaction;
                assertThrows(IllegalStateException.class, () -> storage.transaction(nested -> null));
                assertThrows(IllegalStateException.class, () -> storage.update("INSERT INTO values_table VALUES (1)", SqlParameters.none()));
                var worker = Executors.newSingleThreadExecutor();
                try {
                    assertThrows(java.util.concurrent.ExecutionException.class, () -> worker.submit(
                            () -> transaction.update("INSERT INTO values_table VALUES (1)", SqlParameters.none())).get());
                } finally { worker.shutdownNow(); }
                return null;
            });
            assertThrows(IllegalStateException.class, () -> escaped[0].update("INSERT INTO values_table VALUES (1)", SqlParameters.none()));
            assertEquals(0, storage.queryList("SELECT value FROM values_table", SqlParameters.none(), row -> row.getInt(1)).size());
        }
    }

    @Test void rollbackFailureIsSuppressedOnTheOriginalCallbackFailure() {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite::memory:");
        DataSource rollbackFailing = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        Connection delegate = (Connection) method.invoke(sqlite, args);
                        return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                                (connectionProxy, connectionMethod, connectionArgs) -> {
                                    if (connectionMethod.getName().equals("rollback")) throw new SQLException("rollback failed");
                                    try { return connectionMethod.invoke(delegate, connectionArgs); }
                                    catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                                });
                    }
                    try { return method.invoke(sqlite, args); }
                    catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                });
        try (Storage storage = Storage.builder().dataSource(rollbackFailing).build()) {
            IllegalArgumentException original = new IllegalArgumentException("callback failure");
            StorageException failure = assertThrows(StorageException.class,
                    () -> storage.transaction(transaction -> { throw original; }));
            assertSame(original, failure.getCause());
            assertEquals(1, original.getSuppressed().length);
            assertEquals("rollback failed", original.getSuppressed()[0].getMessage());
        }
    }

    @Test void concurrentAtomicIncrementsDoNotLoseUpdates() throws Exception {
        try (Storage storage = Storage.sqlite(temporary.resolve("concurrent.db"))) {
            storage.update("CREATE TABLE counter(value INTEGER NOT NULL)", SqlParameters.none());
            storage.update("INSERT INTO counter VALUES (0)", SqlParameters.none());
            var workers = Executors.newFixedThreadPool(4);
            try {
                List<java.util.concurrent.Future<?>> tasks = new ArrayList<>();
                for (int worker = 0; worker < 4; worker++) tasks.add(workers.submit(() -> {
                    for (int i = 0; i < 50; i++)
                        storage.transaction(transaction -> transaction.update("UPDATE counter SET value = value + 1", SqlParameters.none()));
                }));
                for (var task : tasks) task.get();
            } finally { workers.shutdownNow(); }
            assertEquals(200, storage.queryOne("SELECT value FROM counter", SqlParameters.none(), row -> row.getInt(1)).orElseThrow());
        }
    }
}
