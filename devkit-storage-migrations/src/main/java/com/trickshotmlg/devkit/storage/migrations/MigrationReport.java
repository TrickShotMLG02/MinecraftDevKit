package com.trickshotmlg.devkit.storage.migrations;

import java.util.Optional;

/** Immutable summary of a successful migration operation. */
public record MigrationReport(int migrationsExecuted, Optional<String> currentVersion) {
    public MigrationReport {
        if (migrationsExecuted < 0) throw new IllegalArgumentException("migrationsExecuted must not be negative");
        currentVersion = currentVersion == null ? Optional.empty() : currentVersion;
    }
}
