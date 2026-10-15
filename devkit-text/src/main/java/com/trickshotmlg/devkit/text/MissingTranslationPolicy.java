package com.trickshotmlg.devkit.text;

/** Defines how a provider renders an unresolved translation that has no fallback. */
public enum MissingTranslationPolicy {
    /** Render the key itself. */
    SHOW_KEY,
    /** Render a visible {@code <missing:key>} marker. */
    SHOW_MARKER,
    /** Fail resolution with {@link MissingTranslationException}. */
    ERROR
}
