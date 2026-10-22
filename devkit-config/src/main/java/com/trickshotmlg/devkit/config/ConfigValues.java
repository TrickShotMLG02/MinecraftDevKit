package com.trickshotmlg.devkit.config;

/** Read-only typed view supplied to cross-field schema constraints. */
public interface ConfigValues {
    <T> T get(ConfigKey<T> key);
}
