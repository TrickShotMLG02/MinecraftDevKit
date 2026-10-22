package com.trickshotmlg.devkit.storage;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

/** Immutable validated connection settings. Passwords are copied and redacted from diagnostics. */
public final class StorageConfiguration {
    private final String jdbcUrl;
    private final String username;
    private final char[] password;
    private final int maximumPoolSize;
    private final Duration connectionTimeout;
    private final Duration idleTimeout;
    private final Duration busyTimeout;
    private final boolean sqlite;
    private final boolean sqliteWal;
    private final boolean externalDataSource;

    StorageConfiguration(String jdbcUrl, String username, char[] password, int maximumPoolSize,
                         Duration connectionTimeout, Duration idleTimeout, Duration busyTimeout,
                         boolean sqlite, boolean sqliteWal, boolean externalDataSource) {
        if (externalDataSource ? jdbcUrl != null : jdbcUrl == null || jdbcUrl.isBlank() || !jdbcUrl.startsWith("jdbc:"))
            throw new IllegalArgumentException("A non-empty JDBC URL is required");
        if (maximumPoolSize < 1) throw new IllegalArgumentException("maximumPoolSize must be positive");
        validateTimeout("connectionTimeout", connectionTimeout, false);
        validateTimeout("idleTimeout", idleTimeout, true);
        validateTimeout("busyTimeout", busyTimeout, true);
        if (!sqlite && (busyTimeout != null || sqliteWal))
            throw new IllegalArgumentException("SQLite settings cannot be used with a network database");
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password == null ? null : password.clone();
        this.maximumPoolSize = maximumPoolSize;
        this.connectionTimeout = Objects.requireNonNull(connectionTimeout, "connectionTimeout");
        this.idleTimeout = idleTimeout;
        this.busyTimeout = busyTimeout;
        this.sqlite = sqlite;
        this.sqliteWal = sqliteWal;
        this.externalDataSource = externalDataSource;
    }

    private static void validateTimeout(String name, Duration value, boolean allowZero) {
        if (value == null) return;
        if (value.isNegative() || (!allowZero && value.isZero()))
            throw new IllegalArgumentException(name + " must be " + (allowZero ? "non-negative" : "positive"));
        try { value.toMillis(); }
        catch (ArithmeticException overflow) { throw new IllegalArgumentException(name + " is too large", overflow); }
        if (!value.isZero() && value.toMillis() < 250 && name.equals("connectionTimeout"))
            throw new IllegalArgumentException("connectionTimeout must be at least 250ms");
    }

    public String jdbcUrl() { return jdbcUrl; }
    public String username() { return username; }
    public char[] password() { return password == null ? null : password.clone(); }
    public int maximumPoolSize() { return maximumPoolSize; }
    public Duration connectionTimeout() { return connectionTimeout; }
    public Duration idleTimeout() { return idleTimeout; }
    public Duration busyTimeout() { return busyTimeout; }
    public boolean sqlite() { return sqlite; }
    public boolean sqliteWal() { return sqliteWal; }
    public boolean externalDataSource() { return externalDataSource; }

    @Override public String toString() {
        return "StorageConfiguration[jdbcUrl=" + (externalDataSource ? "<external-data-source>" : jdbcUrl) + ", username=" + username
                + ", password=<redacted>, maximumPoolSize=" + maximumPoolSize
                + ", connectionTimeout=" + connectionTimeout + ", sqlite=" + sqlite + "]";
    }

    void clearPassword() { if (password != null) Arrays.fill(password, '\0'); }
}
