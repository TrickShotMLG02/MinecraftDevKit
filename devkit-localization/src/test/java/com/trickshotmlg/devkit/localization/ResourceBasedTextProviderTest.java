package com.trickshotmlg.devkit.localization;

import com.trickshotmlg.devkit.text.MissingTranslationException;
import com.trickshotmlg.devkit.text.MissingTranslationPolicy;
import com.trickshotmlg.devkit.text.TextRef;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceBasedTextProviderTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @TempDir
    Path translations;

    @Test
    void loadsUtf8FilesAndResolvesRequestedLocaleAtCallTime() throws IOException {
        write("en-US.properties", "greeting=Hello {player}\n");
        write("de-DE.properties", "greeting=Grüß dich {player}\n");
        ResourceBasedTextProvider provider = provider(List.of(Locale.US, Locale.GERMANY), Locale.US,
                MissingTranslationPolicy.SHOW_KEY, new ArrayList<>());

        assertEquals("Hello Alice", PLAIN.serialize(provider.resolve(
                TextRef.translated("greeting"), Locale.US, Map.of("player", "Alice"))));
        assertEquals("Grüß dich Alice", PLAIN.serialize(provider.resolve(
                TextRef.translated("greeting"), Locale.GERMANY, Map.of("player", "Alice"))));
    }

    @Test
    void prefersExactLocaleThenLanguageThenDefaultLocale() throws IOException {
        write("en-US.properties", "exact=English\ndefaultOnly=Default\n");
        write("de.properties", "exact=Deutsch\nlanguageOnly=Deutsch\n");
        write("de-AT.properties", "exact=Österreichisch\n");
        List<TextDiagnostic> events = new ArrayList<>();
        ResourceBasedTextProvider provider = provider(List.of(Locale.US, Locale.GERMAN,
                Locale.forLanguageTag("de-AT")), Locale.US, MissingTranslationPolicy.SHOW_KEY, events);

        assertEquals("Österreichisch", resolve(provider, "exact", "de-AT"));
        assertEquals("Deutsch", resolve(provider, "languageOnly", "de-AT"));
        assertEquals("Default", resolve(provider, "defaultOnly", "de-AT"));
        assertEquals(List.of(TextDiagnostic.Outcome.LANGUAGE_FALLBACK,
                        TextDiagnostic.Outcome.DEFAULT_LOCALE_FALLBACK),
                events.stream().map(TextDiagnostic::outcome).toList());
    }

    @Test
    void referenceFallbackIsUsedAndMissingTranslationIsStillReported() throws IOException {
        write("en-US.properties", "unrelated=English\n");
        write("de-DE.properties", "unrelated=Deutsch\n");
        List<TextDiagnostic> events = new ArrayList<>();
        ResourceBasedTextProvider provider = provider(List.of(Locale.US, Locale.GERMANY), Locale.US,
                MissingTranslationPolicy.ERROR, events);

        assertEquals("Fallback text", PLAIN.serialize(provider.resolve(
                TextRef.translated("missing.with.fallback", "Fallback text"), Locale.GERMANY)));
        assertEquals(1, events.size());
        assertTrue(events.get(0).referenceFallbackAvailable());
        assertEquals(TextDiagnostic.Outcome.REFERENCE_FALLBACK, events.get(0).outcome());
    }

    @Test
    void missingKeyPolicyReportsAndDisplaysKeyOrMarker() throws IOException {
        write("en-US.properties", "available=Yes\n");
        List<TextDiagnostic> events = new ArrayList<>();
        ResourceBasedTextProvider showKey = provider(List.of(Locale.US), Locale.US,
                MissingTranslationPolicy.SHOW_KEY, events);

        assertEquals("missing.key", resolve(showKey, "missing.key", "en-US"));
        ResourceBasedTextProvider showMarker = provider(List.of(Locale.US), Locale.US,
                MissingTranslationPolicy.SHOW_MARKER, new ArrayList<>());
        assertEquals("<missing:missing.key>", resolve(showMarker, "missing.key", "en-US"));
        assertEquals(TextDiagnostic.Outcome.SHOW_KEY, events.get(0).outcome());
        assertFalse(events.get(0).referenceFallbackAvailable());
    }

    @Test
    void errorPolicyThrowsSpecificExceptionAfterDiagnostic() throws IOException {
        write("en-US.properties", "available=Yes\n");
        List<TextDiagnostic> events = new ArrayList<>();
        ResourceBasedTextProvider provider = provider(List.of(Locale.US), Locale.US,
                MissingTranslationPolicy.ERROR, events);

        MissingTranslationException exception = assertThrows(MissingTranslationException.class,
                () -> resolve(provider, "missing.key", "en-US"));
        assertEquals("missing.key", exception.key());
        assertEquals(1, events.size());
        assertEquals(TextDiagnostic.Outcome.ERROR, events.get(0).outcome());
    }

    @Test
    void repeatedMissingKeysAreDeduplicatedPerLocaleAndKind() throws IOException {
        write("en-US.properties", "available=Yes\n");
        List<TextDiagnostic> events = new ArrayList<>();
        ResourceBasedTextProvider provider = provider(List.of(Locale.US), Locale.US,
                MissingTranslationPolicy.SHOW_KEY, events);

        resolve(provider, "missing.key", "de-DE");
        resolve(provider, "missing.key", "de-DE");
        resolve(provider, "missing.key", "fr-FR");

        assertEquals(2, events.size());
        assertEquals(List.of("de-DE", "fr-FR"), events.stream()
                .map(event -> event.requestedLocale().toLanguageTag()).toList());
    }

    @Test
    void unresolvedPlaceholdersRemainVisibleAndAreReportedOnce() throws IOException {
        write("en-US.properties", "welcome=Welcome {player}, rank {rank}.\n");
        List<TextDiagnostic> events = new ArrayList<>();
        ResourceBasedTextProvider provider = provider(List.of(Locale.US), Locale.US,
                MissingTranslationPolicy.ERROR, events);

        String message = PLAIN.serialize(provider.resolve(TextRef.translated("welcome"), Locale.US,
                Map.of("player", "Alex"), Optional.of("friends.welcome")));
        provider.resolve(TextRef.translated("welcome"), Locale.US, Map.of("player", "Alex"));

        assertEquals("Welcome Alex, rank {rank}.", message);
        assertEquals(1, events.size());
        assertEquals(TextDiagnostic.Kind.MISSING_PLACEHOLDER, events.get(0).kind());
        assertEquals(Optional.of("rank"), events.get(0).placeholder());
        assertEquals(Optional.of("friends.welcome"), events.get(0).sourceContext());
    }

    @Test
    void diagnosticsAreStructuredAndMissingLocaleFallsBackToDefault() throws IOException {
        write("en-US.properties", "welcome=Welcome\n");
        List<TextDiagnostic> events = new ArrayList<>();
        ResourceBasedTextProvider provider = provider(List.of(Locale.US), Locale.US,
                MissingTranslationPolicy.SHOW_KEY, events);

        assertEquals("Welcome", resolve(provider, "welcome", "de-DE"));
        assertEquals(TextDiagnostic.Outcome.DEFAULT_LOCALE_FALLBACK, events.get(0).outcome());
        assertEquals(Locale.GERMANY, events.get(0).requestedLocale());
    }

    @Test
    void distinguishesDefaultLocaleFallbackFromLanguageCatalogFallback() throws IOException {
        write("de-DE.properties", "welcome=Willkommen\n");
        write("en-US.properties", "other=Other\n");
        List<TextDiagnostic> events = new ArrayList<>();
        ResourceBasedTextProvider provider = provider(List.of(Locale.GERMANY, Locale.US), Locale.GERMANY,
                MissingTranslationPolicy.SHOW_KEY, events);

        assertEquals("Willkommen", resolve(provider, "welcome", "de-CH"));
        assertEquals(TextDiagnostic.Outcome.DEFAULT_LOCALE_FALLBACK, events.get(0).outcome());
    }

    @Test
    void classpathLoaderRequiresEveryConfiguredResource() {
        assertThrows(IllegalArgumentException.class, () -> ResourceBasedTextProvider.fromClasspath(
                getClass().getClassLoader(), "missing-localization-resources", List.of(Locale.US), Locale.US,
                MissingTranslationPolicy.SHOW_KEY, TextDiagnosticSink.NO_OP));
    }

    @Test
    void catalogsAndProviderMapsAreImmutable() throws IOException {
        write("en-US.properties", "z=last\na=first\n");
        ResourceBasedTextProvider provider = provider(List.of(Locale.US), Locale.US,
                MissingTranslationPolicy.SHOW_KEY, new ArrayList<>());

        assertEquals(List.of("a", "z"), List.copyOf(provider.catalogs().get(Locale.US).translations().keySet()));
        assertThrows(UnsupportedOperationException.class,
                () -> provider.catalogs().put(Locale.GERMANY, TranslationCatalog.of(Locale.GERMANY, Map.of())));
        assertThrows(UnsupportedOperationException.class,
                () -> provider.catalogs().get(Locale.US).translations().put("new", "value"));
    }

    @Test
    void rejectsMissingDirectoriesInvalidLocalesAndDuplicateCatalogs() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> ResourceBasedTextProvider.fromDirectory(
                translations.resolve("absent"), Locale.US, MissingTranslationPolicy.SHOW_KEY,
                TextDiagnosticSink.NO_OP));
        write("not_a_locale.properties", "key=value\n");
        assertThrows(IllegalArgumentException.class, () -> ResourceBasedTextProvider.fromDirectory(
                translations, Locale.US, MissingTranslationPolicy.SHOW_KEY, TextDiagnosticSink.NO_OP));
        TranslationCatalog catalog = TranslationCatalog.of(Locale.US, Map.of());
        assertThrows(IllegalArgumentException.class, () -> ResourceBasedTextProvider.of(
                List.of(catalog, catalog), Locale.US, MissingTranslationPolicy.SHOW_KEY, TextDiagnosticSink.NO_OP));
    }

    @Test
    void invalidTranslationKeysAndValuesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TranslationCatalog.of(Locale.US, Map.of("bad key", "value")));
        Map<String, String> nullTranslation = new HashMap<>();
        nullTranslation.put("key", null);
        assertThrows(NullPointerException.class, () -> TranslationCatalog.of(Locale.US, nullTranslation));
    }

    private ResourceBasedTextProvider provider(List<Locale> locales, Locale defaultLocale,
                                               MissingTranslationPolicy policy, List<TextDiagnostic> events) {
        ResourceBasedTextProvider provider = ResourceBasedTextProvider.fromDirectory(
                translations, defaultLocale, policy, events::add);
        assertEquals(Set.copyOf(locales), provider.catalogs().keySet());
        return provider;
    }

    private String resolve(ResourceBasedTextProvider provider, String key, String locale) {
        return PLAIN.serialize(provider.resolve(TextRef.translated(key), Locale.forLanguageTag(locale)));
    }

    private void write(String fileName, String value) throws IOException {
        Files.writeString(translations.resolve(fileName), value);
    }
}
