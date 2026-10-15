package com.trickshotmlg.devkit.text;

import net.kyori.adventure.text.Component;

import java.util.Locale;
import java.util.Map;

/** Resolves text references for a locale and optional named placeholder values. */
@FunctionalInterface
public interface TextProvider {
    /**
     * Resolves a reference at the time it is needed.
     *
     * <p>Placeholder values are inserted as plain text and must not be interpreted as markup.</p>
     *
     * @param reference reference to resolve
     * @param locale requested locale; providers must not silently substitute a process-global locale
     * @param placeholders placeholder names and values, or an empty map
     * @return rendered Adventure component
     */
    Component resolve(TextRef reference, Locale locale, Map<String, ?> placeholders);

    /**
     * Resolves a reference without placeholders.
     *
     * @param reference reference to resolve
     * @param locale requested locale
     * @return rendered component
     */
    default Component resolve(TextRef reference, Locale locale) {
        return resolve(reference, locale, Map.of());
    }
}
