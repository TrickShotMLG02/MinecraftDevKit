package com.trickshotmlg.devkit.config;

/**
 * Converts between a decoded source value and a typed configuration value. Implementations should
 * return immutable values (or immutable value objects); the framework defensively freezes standard
 * lists, maps, and sets but cannot copy arbitrary application-defined object graphs.
 */
public interface ConfigCodec<T> {
    /** Human-readable type name used in validation diagnostics. */
    String description();

    /** Decodes a value produced by a configuration source. */
    T decode(Object value) throws IllegalArgumentException;

    /** Encodes a typed value into a representation supported by configuration sources. */
    Object encode(T value);
}
