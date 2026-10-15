package com.trickshotmlg.devkit.localization;

import java.io.IOException;
import java.util.Map;
import java.util.Locale;
import java.nio.file.Path;
import java.util.List;

/**
 * A reloadable source of immutable locale catalogs. A source combines its own files at one
 * priority; duplicate keys within that source must be rejected by its loader.
 */
@FunctionalInterface
public interface TranslationSource {
    /** Loads all locale catalogs from this source. */
    Map<Locale, TranslationCatalog> load() throws IOException;

    /** Source precedence for a translation at the same locale and key. */
    default Layer layer() { return Layer.EXTERNAL; }

    /** External directories that may be watched for changes; bundled sources return none. */
    default List<Path> watchDirectories() { return List.of(); }

    /** Human-readable identifier used in reload diagnostics. */
    default String description() { return getClass().getSimpleName(); }

    /** Supported source priorities. Locale specificity is evaluated before this priority. */
    enum Layer { BUNDLED, EXTERNAL }
}
