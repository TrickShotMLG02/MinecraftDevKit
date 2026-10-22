package com.trickshotmlg.devkit.storage.migrations;

/** A migration engine failure with its original validation or JDBC cause preserved. */
public final class StorageMigrationException extends RuntimeException {
    public StorageMigrationException(String message, Throwable cause) { super(message, cause); }
}
