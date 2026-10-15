package com.trickshotmlg.devkit.localization;

import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Immutable strictness and locale settings for a localization scan. */
public record LocalizationScanOptions(Locale baseLocale, Set<Locale> requiredLocales,
                                      Set<LocalizationFinding.Category> failOn,
                                      Set<String> ignoredKeys) {
    public LocalizationScanOptions {
        Objects.requireNonNull(baseLocale, "baseLocale");
        TreeSet<Locale> orderedLocales = new TreeSet<>(java.util.Comparator.comparing(Locale::toLanguageTag));
        orderedLocales.addAll(requiredLocales);
        requiredLocales = Collections.unmodifiableSet(orderedLocales);
        failOn = Set.copyOf(failOn);
        ignoredKeys = Collections.unmodifiableSet(new TreeSet<>(ignoredKeys));
    }
    /** Defaults to failing on missing translations without fallback and placeholder mismatches. */
    /**
     * Creates the recommended validation policy.
     * @param baseLocale locale used for placeholder comparison
     * @param requiredLocales locales that must be covered
     * @return default options
     */
    public static LocalizationScanOptions defaults(Locale baseLocale, Set<Locale> requiredLocales) {
        return new LocalizationScanOptions(baseLocale, requiredLocales,
                Set.of(LocalizationFinding.Category.MISSING_FALLBACK,
                        LocalizationFinding.Category.PLACEHOLDER_MISMATCH,
                        LocalizationFinding.Category.INVALID_RESOURCE), Set.of());
    }
}
