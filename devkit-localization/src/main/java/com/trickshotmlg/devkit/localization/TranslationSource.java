package com.trickshotmlg.devkit.localization;

import java.io.IOException;
import java.util.Map;
import java.util.Locale;

/**
 * A reloadable source of immutable locale catalogs. A source combines its own files at one
 * priority; duplicate keys within that source must be rejected by its loader.
 */
@FunctionalInterface
public interface TranslationSource {
    /** Loads all locale catalogs from this source. */
    Map<Locale, TranslationCatalog> load() throws IOException;

    /** Human-readable identifier used in reload diagnostics. */
    default String description() { return getClass().getSimpleName(); }
}
