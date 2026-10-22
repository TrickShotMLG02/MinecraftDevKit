package com.trickshotmlg.devkit.config.yaml;

/** One migration step that transforms a detached, mutable dotted-path candidate. */
@FunctionalInterface
public interface ConfigMigration {
    void migrate(MutableConfigValues values) throws Exception;
}
