package com.trickshotmlg.devkit.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

final class JdbcStorage implements Storage {
    private final StorageConfiguration configuration;
    private final DataSource dataSource;
    private final boolean ownsDataSource;
    private final AtomicBoolean closed = new AtomicBoolean();

    JdbcStorage(StorageConfiguration configuration, DataSource external, boolean ownsExternal) throws Exception {
        this.configuration = configuration;
        if (external != null) {
            dataSource = external;
            ownsDataSource = ownsExternal;
            return;
        }
        if (configuration.sqliteWal()) StorageBuilder.enableWal(configuration.jdbcUrl());
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(configuration.jdbcUrl());
        if (configuration.username() != null) hikari.setUsername(configuration.username());
        char[] password = configuration.password();
        if (password != null) {
            hikari.setPassword(new String(password));
            java.util.Arrays.fill(password, '\0');
        }
        hikari.setMaximumPoolSize(configuration.maximumPoolSize());
        hikari.setConnectionTimeout(configuration.connectionTimeout().toMillis());
        if (configuration.idleTimeout() != null && !configuration.idleTimeout().isZero())
            hikari.setIdleTimeout(configuration.idleTimeout().toMillis());
        hikari.setPoolName("MinecraftDevKitStorage");
        if (configuration.sqlite()) {
            hikari.setConnectionInitSql("PRAGMA foreign_keys=ON");
            Properties properties = new Properties();
            properties.setProperty("busy_timeout", Long.toString(configuration.busyTimeout().toMillis()));
            hikari.setDataSourceProperties(properties);
        }
        try {
            dataSource = new HikariDataSource(hikari);
            ownsDataSource = true;
        } catch (RuntimeException failure) {
            configuration.clearPassword();
            throw new StorageException("Could not create JDBC connection pool", failure);
        }
    }

    @Override public StorageConfiguration configuration() { return configuration; }
    @Override public DataSource dataSource() {
        ensureOpen();
        return dataSource;
    }
    @Override public boolean isClosed() { return closed.get(); }
    void ensureOpen() { if (closed.get()) throw new StorageClosedException(); }

    @Override public <T> Optional<T> queryOne(String sql, SqlParameters parameters, RowMapper<T> mapper) {
        List<T> rows = executeQuery(sql, parameters, mapper, true);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override public <T> List<T> queryList(String sql, SqlParameters parameters, RowMapper<T> mapper) {
        return executeQuery(sql, parameters, mapper, false);
    }

    private <T> List<T> executeQuery(String sql, SqlParameters parameters, RowMapper<T> mapper, boolean atMostOne) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(mapper, "mapper");
        validateSql(sql);
        ensureOpen();
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            parameters.bind(statement);
            try (var results = statement.executeQuery()) {
                List<T> mapped = new ArrayList<>(atMostOne ? 1 : 8);
                while (results.next()) {
                    if (atMostOne && !mapped.isEmpty())
                        throw new StorageException("Query expected at most one result row but returned multiple rows");
                    Row row = new Row(results);
                    try {
                        T value = mapper.map(row);
                        if (value == null) throw new StorageException("Row mapper returned null");
                        mapped.add(value);
                    } catch (StorageException failure) {
                        throw failure;
                    } catch (Exception failure) {
                        throw new StorageException("Could not map SQL result row", failure);
                    } finally {
                        row.invalidate();
                    }
                }
                return List.copyOf(mapped);
            }
        } catch (StorageException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new StorageException("Database query failed", failure);
        }
    }

    @Override public int update(String sql, SqlParameters parameters) {
        Objects.requireNonNull(parameters, "parameters");
        validateSql(sql);
        ensureOpen();
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            parameters.bind(statement);
            return statement.executeUpdate();
        } catch (StorageException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new StorageException("Database update failed", failure);
        }
    }

    @Override public OptionalLong insert(String sql, SqlParameters parameters) {
        Objects.requireNonNull(parameters, "parameters");
        validateSql(sql);
        ensureOpen();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            parameters.bind(statement);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                return keys.next() ? OptionalLong.of(keys.getLong(1)) : OptionalLong.empty();
            }
        } catch (StorageException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new StorageException("Database insert failed", failure);
        }
    }

    private static void validateSql(String sql) {
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("SQL must not be empty");
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        configuration.clearPassword();
        if (!ownsDataSource) return;
        try {
            if (dataSource instanceof AutoCloseable closeable) closeable.close();
        } catch (Exception failure) {
            throw new StorageException("Could not close storage resources", failure);
        }
    }
}
