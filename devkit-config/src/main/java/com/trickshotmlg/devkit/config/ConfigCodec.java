package com.trickshotmlg.devkit.config;

/** Converts between a decoded source value and an immutable typed configuration value. */
public interface ConfigCodec<T> {
    /** Human-readable type name used in validation diagnostics. */
    String description();

    /** Decodes a value produced by a configuration source. */
    T decode(Object value) throws IllegalArgumentException;

    /** Encodes a typed value into a representation supported by configuration sources. */
    Object encode(T value);
}
