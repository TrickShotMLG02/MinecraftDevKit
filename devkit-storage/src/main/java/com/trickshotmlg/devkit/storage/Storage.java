package com.trickshotmlg.devkit.storage;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Platform-independent JDBC storage with explicit SQL and typed result mapping. */
public interface Storage extends AutoCloseable {
    static StorageBuilder builder() { return new StorageBuilder(); }
    static Storage sqlite(Path file) { return builder().sqlite(file).build(); }
    static Storage sqliteMemory() { return builder().sqliteMemory().build(); }

    /** Safe, immutable storage settings. Passwords are redacted by toString(). */
    StorageConfiguration configuration();

    /**
     * Returns the storage's DataSource for integrations such as schema migration engines.
     * The DataSource is borrowed: callers must not close it and should close every borrowed Connection.
     */
    DataSource dataSource();

    /** Returns the first row and verifies that the query produced no second row. */
    <T> Optional<T> queryOne(String sql, SqlParameters parameters, RowMapper<T> mapper);

    /** Maps every row eagerly; no JDBC resources escape this call. */
    <T> List<T> queryList(String sql, SqlParameters parameters, RowMapper<T> mapper);

    /** Executes a prepared update and returns the affected row count. */
    int update(String sql, SqlParameters parameters);

    /** Executes an insert and returns the driver's generated key when available. */
    java.util.OptionalLong insert(String sql, SqlParameters parameters);

    /** Executes work synchronously on one connection and commits only if the callback succeeds. */
    <T> T transaction(TransactionWork<T> work);

    /** Returns the bounded asynchronous operation facade. */
    StorageAsync async();

    boolean isClosed();

    /** Releases resources owned by this instance. Safe to call more than once. */
    @Override void close();
}
