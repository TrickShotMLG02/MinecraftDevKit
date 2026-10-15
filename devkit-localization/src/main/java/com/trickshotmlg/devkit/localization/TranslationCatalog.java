package com.trickshotmlg.devkit.localization;

import com.trickshotmlg.devkit.text.TextRef;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable translation entries for one locale. */
public final class TranslationCatalog {
    private final Locale locale;
    private final Map<String, String> translations;

    private TranslationCatalog(Locale locale, Map<String, String> translations) {
        this.locale = Objects.requireNonNull(locale, "locale");
        TreeMap<String, String> sorted = new TreeMap<>();
        Objects.requireNonNull(translations, "translations").forEach((key, value) -> {
            TextRef.translated(key);
            sorted.put(key, Objects.requireNonNull(value, "translation value for " + key));
        });
        this.translations = Collections.unmodifiableMap(sorted);
    }

    /**
     * Creates an immutable catalog.
     *
     * @param locale catalog locale
     * @param translations translation key/value pairs
     * @return immutable catalog
     */
    public static TranslationCatalog of(Locale locale, Map<String, String> translations) {
        return new TranslationCatalog(locale, translations);
    }

    /**
     * Returns the locale represented by this catalog.
     *
     * @return catalog locale
     */
    public Locale locale() {
        return locale;
    }

    /**
     * Returns an immutable, key-sorted view of the translations.
     *
     * @return immutable translation entries
     */
    public Map<String, String> translations() {
        return translations;
    }
}
