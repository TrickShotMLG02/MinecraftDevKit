package com.trickshotmlg.devkit.storage;

import javax.sql.DataSource;
import java.nio.file.Path;

/** Platform-independent JDBC storage. Database operations and async execution are added by this module's API. */
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

    boolean isClosed();

    /** Releases resources owned by this instance. Safe to call more than once. */
    @Override void close();
}
