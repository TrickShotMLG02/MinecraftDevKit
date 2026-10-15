package com.trickshotmlg.devkit.localization;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Builds deterministic BCP 47 parent-locale chains for localization lookup. */
public final class LocaleFallbackResolver {
    private LocaleFallbackResolver() { }

    /**
     * Normalizes a locale by removing extensions, which do not identify separate translation
     * catalogs. The language, script, region, and variant are retained.
     */
    public static Locale normalize(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        if (locale.getLanguage().isEmpty() || locale.toLanguageTag().equals("und")) {
            throw new IllegalArgumentException("Locale must contain a language: " + locale);
        }
        Locale normalized = new Locale.Builder().setLocale(locale).clearExtensions().build();
        if (normalized.getLanguage().isEmpty()) throw new IllegalArgumentException("Locale must contain a language: " + locale);
        return normalized;
    }

    /** Parses and canonicalizes a strict BCP 47 locale tag. */
    public static Locale parse(String languageTag) {
        Objects.requireNonNull(languageTag, "languageTag");
        try {
            Locale parsed = new Locale.Builder().setLanguageTag(languageTag).build();
            if (parsed.getLanguage().isEmpty() || !parsed.toLanguageTag().equalsIgnoreCase(languageTag)) {
                throw new IllegalArgumentException("Locale must be a valid BCP 47 language tag: " + languageTag);
            }
            return normalize(parsed);
        } catch (java.util.IllformedLocaleException exception) {
            throw new IllegalArgumentException("Invalid BCP 47 locale tag: " + languageTag, exception);
        }
    }

    /**
     * Returns the locale followed by progressively less specific parents. Subtags are removed
     * from the right in BCP 47 order: variant, region, then script. Extensions are discarded.
     */
    public static List<Locale> parents(Locale locale) {
        Locale current = normalize(locale);
        ArrayList<Locale> result = new ArrayList<>();
        result.add(current);
        String variant = current.getVariant();
        while (!variant.isEmpty()) {
            int last = variant.lastIndexOf('_');
            variant = last < 0 ? "" : variant.substring(0, last);
            current = build(current.getLanguage(), current.getScript(), current.getCountry(), variant);
            addDistinct(result, current);
        }
        if (!current.getCountry().isEmpty()) {
            current = build(current.getLanguage(), current.getScript(), "", "");
            addDistinct(result, current);
        }
        if (!current.getScript().isEmpty()) {
            current = build(current.getLanguage(), "", "", "");
            addDistinct(result, current);
        }
        return List.copyOf(result);
    }

    /**
     * Returns requested parents followed, when enabled, by configured fallback parents. Repeated
     * locales are emitted once at their first occurrence.
     */
    public static List<Locale> chain(Locale requested, Locale defaultFallback, boolean fallbackEnabled) {
        LinkedHashSet<Locale> locales = new LinkedHashSet<>(parents(requested));
        if (fallbackEnabled && defaultFallback != null) locales.addAll(parents(defaultFallback));
        return List.copyOf(locales);
    }

    private static Locale build(String language, String script, String region, String variant) {
        Locale.Builder builder = new Locale.Builder().setLanguage(language);
        if (!script.isEmpty()) builder.setScript(script);
        if (!region.isEmpty()) builder.setRegion(region);
        if (!variant.isEmpty()) builder.setVariant(variant);
        return builder.build();
    }

    private static void addDistinct(List<Locale> locales, Locale candidate) {
        if (!locales.contains(candidate)) locales.add(candidate);
    }
}
