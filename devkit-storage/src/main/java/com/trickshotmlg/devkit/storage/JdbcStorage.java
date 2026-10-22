package com.trickshotmlg.devkit.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.Properties;
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
