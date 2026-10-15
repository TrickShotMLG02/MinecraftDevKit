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
    /**
     * Loads all locale catalogs from this source.
     * @return catalog map
     * @throws IOException if the source cannot be read or validated
     */
    Map<Locale, TranslationCatalog> load() throws IOException;

    /** Source precedence for a translation at the same locale and key. @return source layer */
    default Layer layer() { return Layer.EXTERNAL; }

    /** External directories that may be watched for changes; bundled sources return none. @return watch roots */
    default List<Path> watchDirectories() { return List.of(); }

    /** Human-readable identifier used in reload diagnostics. @return source description */
    default String description() { return getClass().getSimpleName(); }

    /** Supported source priorities. Locale specificity is evaluated before this priority. */
    enum Layer {
        /** Packaged defaults distributed by the application. */
        BUNDLED,
        /** User-editable files that override bundled values at the same locale. */
        EXTERNAL
    }
}
