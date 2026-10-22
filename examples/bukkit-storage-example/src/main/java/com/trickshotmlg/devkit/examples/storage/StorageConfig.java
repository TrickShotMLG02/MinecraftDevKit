package com.trickshotmlg.devkit.examples.storage;

import com.trickshotmlg.devkit.config.ConfigKey;
import com.trickshotmlg.devkit.config.ConfigSchema;

/** Typed settings for choosing a built-in SQLite database or an optional network JDBC backend. */
final class StorageConfig {
    enum Backend { SQLITE, MYSQL, MARIADB }
    static final ConfigKey<Backend> BACKEND =
            ConfigKey.enumeration("database.type", Backend.class, Backend.SQLITE).build();
    static final ConfigKey<String> SQLITE_FILE =
            ConfigKey.string("database.sqlite.filename", "players.db").notBlank().build();
    static final ConfigKey<String> HOST =
            ConfigKey.string("database.mysql.host", "localhost").notBlank().build();
    static final ConfigKey<Integer> PORT =
            ConfigKey.integer("database.mysql.port", 3306).range(1, 65535).build();
    static final ConfigKey<String> DATABASE =
            ConfigKey.string("database.mysql.database", "minecraft").notBlank().build();
    static final ConfigKey<String> USERNAME =
            ConfigKey.string("database.mysql.username", "minecraft").notBlank().build();
    static final ConfigKey<String> PASSWORD =
            ConfigKey.string("database.mysql.password", "").sensitive().build();
    static final ConfigKey<Integer> POOL_SIZE =
            ConfigKey.integer("database.mysql.pool-size", 8).range(1, 32).build();
    static final ConfigSchema SCHEMA = ConfigSchema.builder().version(1)
            .register(BACKEND, SQLITE_FILE, HOST, PORT, DATABASE, USERNAME, PASSWORD, POOL_SIZE)
            .build();
    private StorageConfig() { }
}
