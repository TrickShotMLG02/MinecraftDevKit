package com.trickshotmlg.devkit.localization;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Structured runtime diagnostic emitted while resolving localized text. */
public record TextDiagnostic(
        Kind kind,
        String translationKey,
        Locale requestedLocale,
        boolean referenceFallbackAvailable,
        Outcome outcome,
        Optional<String> placeholder,
        Optional<String> sourceContext
) {
    /**
     * Validates diagnostic fields and optional metadata.
     *
     * @param kind diagnostic category
     * @param translationKey referenced key
     * @param requestedLocale caller's requested locale
     * @param referenceFallbackAvailable whether the reference contains fallback text
     * @param outcome resolution outcome
     * @param placeholder unresolved placeholder name, if applicable
     * @param sourceContext caller-provided diagnostic context
     */
    public TextDiagnostic {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(translationKey, "translationKey");
        Objects.requireNonNull(requestedLocale, "requestedLocale");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(placeholder, "placeholder");
        Objects.requireNonNull(sourceContext, "sourceContext");
    }

    /** Diagnostic category. */
    public enum Kind {
        /** A referenced translation was unavailable in the requested locale. */
        MISSING_TRANSLATION,
        /** The resolved text contains a placeholder without a supplied value. */
        MISSING_PLACEHOLDER
    }

    /** Resolution path or issue represented by a diagnostic. */
    public enum Outcome {
        /** A language-only catalog provided the value. */
        LANGUAGE_FALLBACK,
        /** A script, region, or variant parent locale provided the value. */
        PARENT_LOCALE_FALLBACK,
        /** The configured default locale provided the value. */
        DEFAULT_LOCALE_FALLBACK,
        /** The reference's own fallback provided the value. */
        REFERENCE_FALLBACK,
        /** The key itself was displayed. */
        SHOW_KEY,
        /** A visible missing-key marker was displayed. */
        SHOW_MARKER,
        /** Resolution failed because the provider uses the error policy. */
        ERROR,
        /** A placeholder remained visible because no value was supplied. */
        PLACEHOLDER_UNRESOLVED
    }
}
