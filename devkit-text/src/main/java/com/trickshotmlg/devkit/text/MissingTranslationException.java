package com.trickshotmlg.devkit.text;

import java.util.Locale;
import java.util.Objects;

/** Thrown when a provider configured with {@link MissingTranslationPolicy#ERROR} cannot resolve a key. */
public final class MissingTranslationException extends RuntimeException {
    /** Missing translation key. */
    private final String key;
    /** Locale used for the failed lookup. */
    private final Locale locale;

    /**
     * Creates an exception identifying the key and locale that could not be resolved.
     *
     * @param key missing translation key
     * @param locale locale requested by the caller
     */
    public MissingTranslationException(String key, Locale locale) {
        super("Missing translation '" + Objects.requireNonNull(key, "key") + "' for locale "
                + Objects.requireNonNull(locale, "locale").toLanguageTag());
        this.key = key;
        this.locale = locale;
    }

    /**
     * Returns the missing translation key.
     *
     * @return translation key
     */
    public String key() {
        return key;
    }

    /**
     * Returns the locale requested by the caller.
     *
     * @return requested locale
     */
    public Locale locale() {
        return locale;
    }
}
