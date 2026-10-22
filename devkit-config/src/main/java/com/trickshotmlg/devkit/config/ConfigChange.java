package com.trickshotmlg.devkit.config;

import java.util.Objects;

/** Notification for one effective value change after atomic publication. */
public record ConfigChange<T>(ConfigKey<T> key, T oldValue, T newValue, Origin origin, long generation) {
    public ConfigChange {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(origin, "origin");
    }
    public enum Origin { PROGRAMMATIC, RELOAD }
}
