package com.trickshotmlg.devkit.localization;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable, validated catalogs combined from bundled and external translation sources. */
public final class TranslationSnapshot {
    private final Map<Locale, CatalogLayers> catalogs;

    private TranslationSnapshot(Map<Locale, CatalogLayers> catalogs) {
        this.catalogs = Collections.unmodifiableMap(catalogs);
    }

    /**
     * Loads and combines sources. Duplicate keys across different sources in the same layer are
     * rejected; external values intentionally override bundled values for the same locale/key.
     */
    public static TranslationSnapshot load(Iterable<? extends TranslationSource> sources) throws IOException {
        Objects.requireNonNull(sources, "sources");
        TreeMap<Locale, MutableLayers> merged = new TreeMap<>(Comparator.comparing(Locale::toLanguageTag));
        for (TranslationSource source : sources) {
            Objects.requireNonNull(source, "source");
            Map<Locale, TranslationCatalog> loaded = Objects.requireNonNull(source.load(),
                    "source returned null catalogs: " + source.description());
            for (Map.Entry<Locale, TranslationCatalog> entry : loaded.entrySet()) {
                Locale locale = LocaleFallbackResolver.normalize(entry.getKey());
                TranslationCatalog catalog = Objects.requireNonNull(entry.getValue(), "catalog");
                if (!locale.equals(LocaleFallbackResolver.normalize(catalog.locale()))) {
                    throw new IOException("Source " + source.description() + " returned mismatched catalog locale for " + locale);
                }
                MutableLayers layers = merged.computeIfAbsent(locale, ignored -> new MutableLayers());
                Map<String, String> destination = source.layer() == TranslationSource.Layer.EXTERNAL
                        ? layers.external : layers.bundled;
                for (Map.Entry<String, String> value : catalog.translations().entrySet()) {
                    if (destination.putIfAbsent(value.getKey(), value.getValue()) != null) {
                        throw new IOException("Duplicate " + source.layer().name().toLowerCase(Locale.ROOT)
                                + " translation key '" + value.getKey() + "' for " + locale.toLanguageTag()
                                + " from source " + source.description());
                    }
                }
                if (source.layer() == TranslationSource.Layer.EXTERNAL) layers.externalSources.add(source.description());
                else layers.bundledSources.add(source.description());
            }
        }
        TreeMap<Locale, CatalogLayers> immutable = new TreeMap<>(Comparator.comparing(Locale::toLanguageTag));
        merged.forEach((locale, layers) -> immutable.put(locale, layers.freeze()));
        return new TranslationSnapshot(immutable);
    }

    /** Creates a snapshot containing only bundled catalogs, useful for immutable in-memory inputs. */
    public static TranslationSnapshot bundled(Map<Locale, TranslationCatalog> catalogs) {
        return fromMap(catalogs, TranslationSource.Layer.BUNDLED);
    }

    /** Creates a snapshot containing only external catalogs, useful for immutable in-memory inputs. */
    public static TranslationSnapshot external(Map<Locale, TranslationCatalog> catalogs) {
        return fromMap(catalogs, TranslationSource.Layer.EXTERNAL);
    }

    private static TranslationSnapshot fromMap(Map<Locale, TranslationCatalog> input, TranslationSource.Layer layer) {
        Objects.requireNonNull(input, "catalogs");
        TreeMap<Locale, MutableLayers> merged = new TreeMap<>(Comparator.comparing(Locale::toLanguageTag));
        input.forEach((locale, catalog) -> {
            Locale normalized = LocaleFallbackResolver.normalize(locale);
            MutableLayers values = new MutableLayers();
            Map<String, String> target = layer == TranslationSource.Layer.EXTERNAL ? values.external : values.bundled;
            target.putAll(Objects.requireNonNull(catalog, "catalog").translations());
            if (merged.putIfAbsent(normalized, values) != null) throw new IllegalArgumentException("Duplicate catalog locale " + normalized);
        });
        TreeMap<Locale, CatalogLayers> immutable = new TreeMap<>(Comparator.comparing(Locale::toLanguageTag));
        merged.forEach((locale, layers) -> immutable.put(locale, layers.freeze()));
        return new TranslationSnapshot(immutable);
    }

    /** Returns available locales in BCP 47 tag order. */
    public List<Locale> locales() { return List.copyOf(catalogs.keySet()); }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof TranslationSnapshot snapshot && catalogs.equals(snapshot.catalogs);
    }

    @Override public int hashCode() { return catalogs.hashCode(); }

    /** Returns the effective, external-over-bundled view for one exact locale. */
    public Map<String, String> translations(Locale locale) {
        CatalogLayers layers = catalogs.get(LocaleFallbackResolver.normalize(locale));
        if (layers == null) return Map.of();
        TreeMap<String, String> merged = new TreeMap<>(layers.bundled);
        merged.putAll(layers.external);
        return Collections.unmodifiableMap(merged);
    }

    /**
     * Looks up a key following locale specificity first and source priority second. This shared
     * operation is intended for runtime resolution and scanner coverage checks.
     */
    public Lookup lookup(String key, Locale requested, Locale defaultFallback, boolean fallbackEnabled) {
        Objects.requireNonNull(key, "key");
        List<Locale> requestedParents = LocaleFallbackResolver.parents(requested);
        for (Locale locale : LocaleFallbackResolver.chain(requested, defaultFallback, fallbackEnabled)) {
            CatalogLayers layers = catalogs.get(locale);
            if (layers == null) continue;
            String value = layers.external.get(key);
            if (value != null) return result(locale, value, TranslationSource.Layer.EXTERNAL, requestedParents,
                    requested, defaultFallback, fallbackEnabled, layers.externalSources);
            value = layers.bundled.get(key);
            if (value != null) return result(locale, value, TranslationSource.Layer.BUNDLED, requestedParents,
                    requested, defaultFallback, fallbackEnabled, layers.bundledSources);
        }
        return null;
    }

    private static Lookup result(Locale locale, String value, TranslationSource.Layer layer,
                                 List<Locale> requestedParents, Locale requested, Locale fallback,
                                 boolean fallbackEnabled, List<String> sourceNames) {
        TextResolution.Fallback fallbackKind;
        if (locale.equals(LocaleFallbackResolver.normalize(requested))) fallbackKind = TextResolution.Fallback.NONE;
        else if (requestedParents.contains(locale)) fallbackKind = TextResolution.Fallback.PARENT_LOCALE;
        else fallbackKind = TextResolution.Fallback.DEFAULT_LOCALE;
        return new Lookup(locale, value, layer, fallbackKind, sourceNames);
    }

    /** One resolved translation and its source/fallback provenance. */
    public record Lookup(Locale locale, String value, TranslationSource.Layer layer,
                         TextResolution.Fallback fallback, List<String> sources) {
        public Lookup {
            Objects.requireNonNull(locale, "locale");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(layer, "layer");
            Objects.requireNonNull(fallback, "fallback");
            sources = List.copyOf(sources);
        }
    }

    private record CatalogLayers(Map<String, String> bundled, Map<String, String> external,
                                 List<String> bundledSources, List<String> externalSources) { }

    private static final class MutableLayers {
        final Map<String, String> bundled = new TreeMap<>();
        final Map<String, String> external = new TreeMap<>();
        final List<String> bundledSources = new ArrayList<>();
        final List<String> externalSources = new ArrayList<>();
        CatalogLayers freeze() {
            return new CatalogLayers(Collections.unmodifiableMap(new TreeMap<>(bundled)),
                    Collections.unmodifiableMap(new TreeMap<>(external)), List.copyOf(bundledSources),
                    List.copyOf(externalSources));
        }
    }
}
