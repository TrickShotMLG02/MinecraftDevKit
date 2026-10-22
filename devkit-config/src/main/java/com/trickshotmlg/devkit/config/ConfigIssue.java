package com.trickshotmlg.devkit.config;

import java.util.Objects;
import java.util.Optional;

/** Structured validation finding. Sensitive values are never included automatically. */
public record ConfigIssue(String path, Severity severity, String message, Optional<String> source,
                          Optional<String> expected) {
    public ConfigIssue {
        path = Objects.requireNonNull(path, "path");
        severity = Objects.requireNonNull(severity, "severity");
        message = Objects.requireNonNull(message, "message");
        source = Objects.requireNonNull(source, "source");
        expected = Objects.requireNonNull(expected, "expected");
    }
    public enum Severity { WARNING, ERROR }
    public static ConfigIssue error(String path, String message) {
        return new ConfigIssue(path, Severity.ERROR, message, Optional.empty(), Optional.empty());
    }
}
