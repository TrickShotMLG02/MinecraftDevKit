package com.trickshotmlg.devkit.text;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An immutable reference to text that is either literal or resolved from a translation key.
 *
 * <p>A translation fallback is displayed when a provider cannot find the key. Providers must
 * still report the missing key independently of whether a fallback is present.</p>
 */
public sealed interface TextRef permits TextRef.Literal, TextRef.Translated {
    /** Creates a reference containing literal text. */
    /**
     * Creates literal text.
     *
     * @param text text to render; it may be empty but not {@code null}
     * @return immutable literal reference
     */
    static TextRef literal(String text) {
        return new Literal(text);
    }

    /** Creates a translation reference without a fallback. */
    /**
     * Creates a translation key without a fallback.
     *
     * @param key translation key
     * @return immutable translated reference
     */
    static TextRef translated(String key) {
        return new Translated(key, Optional.empty());
    }

    /** Creates a translation reference with a fallback. */
    /**
     * Creates a translation key with a display fallback.
     *
     * @param key translation key
     * @param fallback text to display when a provider cannot find the key
     * @return immutable translated reference
     */
    static TextRef translated(String key, String fallback) {
        return new Translated(key, Optional.of(Objects.requireNonNull(fallback, "fallback")));
    }

    /** Returns the translation key, or an empty value for literal references. */
    /**
     * Returns the translation key for translated references.
     *
     * @return key, or empty for a literal
     */
    default Optional<String> translationKey() {
        return this instanceof Translated translated
                ? Optional.of(translated.key())
                : Optional.empty();
    }

    /** Returns the optional fallback text. Literal references do not have a fallback. */
    /**
     * Returns the fallback for translated references.
     *
     * @return fallback, or empty when none is configured
     */
    default Optional<String> fallback() {
        return this instanceof Translated translated ? translated.fallback() : Optional.empty();
    }

    private static boolean isValidKey(String key) {
        return Pattern.matches("[A-Za-z0-9][A-Za-z0-9_.-]*", key);
    }

    /** A fixed literal string. */
    record Literal(String value) implements TextRef {
        /**
         * Validates and stores a literal value.
         *
         * @param value literal text
         */
        public Literal {
            Objects.requireNonNull(value, "value");
        }
    }

    /** A translation key and its optional display fallback. */
    record Translated(String key, Optional<String> fallback) implements TextRef {
        /**
         * Validates and stores a translation key and optional fallback.
         *
         * @param key translation key
         * @param fallback optional display fallback
         */
        public Translated {
            Objects.requireNonNull(key, "key");
            if (!TextRef.isValidKey(key)) {
                throw new IllegalArgumentException(
                        "Translation keys must start with a letter or digit and contain only letters, digits, '.', '_' or '-': " + key
                );
            }
            Objects.requireNonNull(fallback, "fallback");
            fallback.ifPresent(value -> Objects.requireNonNull(value, "fallback value"));
        }

        /** Creates a translation with no fallback. */
        /**
         * Creates a translation key without a fallback.
         *
         * @param key translation key
         */
        public Translated(String key) {
            this(key, Optional.empty());
        }

        /** Creates a translation with a fallback string. */
        /**
         * Creates a translation key with a fallback string.
         *
         * @param key translation key
         * @param fallback display fallback
         */
        public Translated(String key, String fallback) {
            this(key, Optional.of(Objects.requireNonNull(fallback, "fallback")));
        }
    }
}
