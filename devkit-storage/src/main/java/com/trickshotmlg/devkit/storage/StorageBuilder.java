package com.trickshotmlg.devkit.storage;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Objects;

/** Builder for HikariCP-backed storage. Externally supplied data sources remain caller-owned by default. */
public final class StorageBuilder {
    private String jdbcUrl;
    private String username;
    private char[] password;
    private int maximumPoolSize = 8;
    private Duration connectionTimeout = Duration.ofSeconds(10);
    private Duration idleTimeout = Duration.ofMinutes(5);
    private Duration busyTimeout;
    private boolean sqlite;
    private boolean sqliteWal;
    private DataSource externalDataSource;
    private boolean ownsExternalDataSource;

    StorageBuilder() { }

    /** Configures file-backed SQLite with conservative single-connection defaults and WAL. */
    public StorageBuilder sqlite(Path file) {
        Objects.requireNonNull(file, "file");
        Path absolute = file.toAbsolutePath().normalize();
        this.jdbcUrl = "jdbc:sqlite:" + absolute;
        this.sqlite = true;
        this.sqliteWal = true;
        this.maximumPoolSize = 1;
        this.busyTimeout = Duration.ofSeconds(5);
        return this;
    }

    /** Configures a single-connection SQLite in-memory database. */
    public StorageBuilder sqliteMemory() {
        this.jdbcUrl = "jdbc:sqlite::memory:";
        this.sqlite = true;
        this.sqliteWal = false;
        this.maximumPoolSize = 1;
        this.busyTimeout = Duration.ofSeconds(5);
        return this;
    }

    /** Configures a generic JDBC URL. Add the matching driver to the application runtime classpath. */
    public StorageBuilder jdbcUrl(String jdbcUrl) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.sqlite = jdbcUrl.startsWith("jdbc:sqlite:");
        if (sqlite) {
            maximumPoolSize = 1;
            busyTimeout = Duration.ofSeconds(5);
        }
        return this;
    }

    public StorageBuilder mysql(String host, int port, String database) {
        return networkUrl("jdbc:mysql://", host, port, database);
    }

    public StorageBuilder mariadb(String host, int port, String database) {
        return networkUrl("jdbc:mariadb://", host, port, database);
    }

    private StorageBuilder networkUrl(String scheme, String host, int port, String database) {
        if (host == null || host.isBlank() || host.contains("/") || host.contains("?") || host.contains("#"))
            throw new IllegalArgumentException("Invalid database host");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("port must be between 1 and 65535");
        if (database == null || !database.matches("[A-Za-z0-9_$-]+"))
            throw new IllegalArgumentException("Database name contains unsupported characters");
        jdbcUrl = scheme + host + ":" + port + "/" + database;
        sqlite = false;
        sqliteWal = false;
        busyTimeout = null;
        maximumPoolSize = 8;
        return this;
    }

    /** Sets credentials; the builder takes a defensive copy of the password. */
    public StorageBuilder credentials(String username, char[] password) {
        this.username = username;
        if (this.password != null) java.util.Arrays.fill(this.password, '\0');
        this.password = password == null ? null : password.clone();
        return this;
    }

    /** Convenience overload. Prefer the char-array overload when the source can avoid immutable strings. */
    public StorageBuilder credentials(String username, String password) {
        return credentials(username, password == null ? null : password.toCharArray());
    }

    public StorageBuilder maximumPoolSize(int size) { maximumPoolSize = size; return this; }
    public StorageBuilder connectionTimeout(Duration timeout) { connectionTimeout = timeout; return this; }
    public StorageBuilder idleTimeout(Duration timeout) { idleTimeout = timeout; return this; }
    public StorageBuilder sqliteBusyTimeout(Duration timeout) { busyTimeout = timeout; return this; }
    public StorageBuilder sqliteWal(boolean enabled) { sqliteWal = enabled; return this; }

    /** Uses a caller-owned DataSource. Closing Storage will not close it. */
    public StorageBuilder dataSource(DataSource dataSource) {
        externalDataSource = Objects.requireNonNull(dataSource, "dataSource");
        ownsExternalDataSource = false;
        return this;
    }

    /** Uses a DataSource and transfers its closeable lifecycle to Storage when {@code takeOwnership} is true. */
    public StorageBuilder dataSource(DataSource dataSource, boolean takeOwnership) {
        externalDataSource = Objects.requireNonNull(dataSource, "dataSource");
        ownsExternalDataSource = takeOwnership;
        return this;
    }

    public Storage build() {
        if (externalDataSource == null && (jdbcUrl == null || jdbcUrl.isBlank()))
            throw new IllegalStateException("Configure sqlite(), mysql(), mariadb(), jdbcUrl(), or dataSource() first");
        if (externalDataSource != null && (sqliteWal || busyTimeout != null))
            throw new IllegalStateException("SQLite initialization options cannot be applied to an external DataSource");
        StorageConfiguration configuration = new StorageConfiguration(jdbcUrl, username, password, maximumPoolSize,
                connectionTimeout, idleTimeout, busyTimeout, sqlite, sqliteWal, externalDataSource != null);
        try {
            if (sqlite && jdbcUrl != null && !jdbcUrl.equals("jdbc:sqlite::memory:")) {
                Path file = Path.of(jdbcUrl.substring("jdbc:sqlite:".length()));
                Path parent = file.getParent();
                if (parent != null) Files.createDirectories(parent);
            }
            return new JdbcStorage(configuration, externalDataSource, ownsExternalDataSource);
        } catch (Exception failure) {
            configuration.clearPassword();
            throw failure instanceof StorageException storageFailure ? storageFailure
                    : new StorageException("Could not initialize storage", failure);
        } finally {
            if (password != null) java.util.Arrays.fill(password, '\0');
        }
    }

    static void enableWal(String jdbcUrl) throws Exception {
        try (var connection = DriverManager.getConnection(jdbcUrl);
             var statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
        }
    }
}
