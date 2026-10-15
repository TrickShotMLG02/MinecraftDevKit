package com.trickshotmlg.devkit.localization;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TranslationSnapshotTest {
    @Test
    void exactBundledLocaleWinsOverExternalParentLocale() throws Exception {
        TranslationSnapshot snapshot = TranslationSnapshot.load(List.of(
                source(TranslationSource.Layer.BUNDLED, "bundle", Map.of(
                        Locale.forLanguageTag("en-GB"), catalog("en-GB", "greeting", "British"))),
                source(TranslationSource.Layer.EXTERNAL, "external", Map.of(
                        Locale.ENGLISH, catalog("en", "greeting", "External English")))));

        TranslationSnapshot.Lookup result = snapshot.lookup("greeting", Locale.forLanguageTag("en-GB"),
                Locale.ENGLISH, true);
        assertEquals("British", result.value());
        assertEquals(TranslationSource.Layer.BUNDLED, result.layer());
        assertEquals(TextResolution.Fallback.NONE, result.fallback());
    }

    @Test
    void externalOverridesBundledForSameLocaleAndKey() throws Exception {
        TranslationSnapshot snapshot = TranslationSnapshot.load(List.of(
                source(TranslationSource.Layer.BUNDLED, "bundle", Map.of(
                        Locale.ENGLISH, catalog("en", "greeting", "Bundled"))),
                source(TranslationSource.Layer.EXTERNAL, "external", Map.of(
                        Locale.ENGLISH, catalog("en", "greeting", "External")))));

        TranslationSnapshot.Lookup result = snapshot.lookup("greeting", Locale.forLanguageTag("en-GB"), null, false);
        assertEquals("External", result.value());
        assertEquals(TranslationSource.Layer.EXTERNAL, result.layer());
        assertEquals(TextResolution.Fallback.PARENT_LOCALE, result.fallback());
    }

    @Test
    void defaultLocaleHierarchyIsUsedOnlyWhenEnabledAndKeepsDistinctProvenance() throws Exception {
        TranslationSnapshot snapshot = TranslationSnapshot.load(List.of(source(TranslationSource.Layer.BUNDLED,
                "bundle", Map.of(Locale.ENGLISH, catalog("en", "greeting", "Hello")))));

        assertEquals(TextResolution.Fallback.DEFAULT_LOCALE,
                snapshot.lookup("greeting", Locale.GERMAN, Locale.forLanguageTag("en-GB"), true).fallback());
        assertEquals(null, snapshot.lookup("greeting", Locale.GERMAN, Locale.forLanguageTag("en-GB"), false));
    }

    @Test
    void rejectsSamePriorityDuplicatesButAllowsCrossLayerOverrides() {
        assertThrows(IOException.class, () -> TranslationSnapshot.load(List.of(
                source(TranslationSource.Layer.BUNDLED, "one", Map.of(Locale.ENGLISH,
                        catalog("en", "key", "one"))),
                source(TranslationSource.Layer.BUNDLED, "two", Map.of(Locale.ENGLISH,
                        catalog("en", "key", "two"))))));
    }

    private static TranslationSource source(TranslationSource.Layer layer, String name,
                                            Map<Locale, TranslationCatalog> catalogs) {
        return new TranslationSource() {
            @Override public Map<Locale, TranslationCatalog> load() { return catalogs; }
            @Override public Layer layer() { return layer; }
            @Override public String description() { return name; }
        };
    }

    private static TranslationCatalog catalog(String tag, String key, String value) {
        return TranslationCatalog.of(Locale.forLanguageTag(tag), Map.of(key, value));
    }
}
