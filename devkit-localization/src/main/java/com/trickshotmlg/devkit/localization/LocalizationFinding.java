package com.trickshotmlg.devkit.localization;

import java.util.Objects;
import java.util.Optional;

/** One deterministic, machine-readable localization diagnostic. */
/** Structured finding returned by the localization scanner. */
public record LocalizationFinding(Severity severity, Category category, Optional<String> locale,
                                  String key, String message, boolean fallbackAvailable) {
    /** Validates the finding fields. */
    public LocalizationFinding {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(message, "message");
    }
    /** Output severity; strictness is separately controlled by scan options. */
    public enum Severity { ERROR, WARNING, INFO }
    /** Machine-readable reason for a finding. */
    public enum Category { MISSING_TRANSLATION, MISSING_FALLBACK, MISSING_LOCALE_COVERAGE,
        UNUSED_TRANSLATION, PLACEHOLDER_MISMATCH, INVALID_RESOURCE, DUPLICATE_REFERENCE }
}
