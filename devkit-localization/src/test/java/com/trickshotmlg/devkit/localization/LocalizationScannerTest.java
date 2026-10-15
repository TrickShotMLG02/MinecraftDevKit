package com.trickshotmlg.devkit.localization;

import com.trickshotmlg.devkit.text.TextCatalog;
import com.trickshotmlg.devkit.text.TextRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class LocalizationScannerTest {
    @TempDir Path temp;
    @Test void reportsFallbackCoverageUnusedAndPlaceholderProblemsDeterministically() {
        TextCatalog refs = TextCatalog.builder()
                .add(TextRef.translated("friends.add", "Added {player}"), "command", "/friends add")
                .add(TextRef.translated("friends.remove"), "command", "/friends remove")
                .build();
        var en = TranslationCatalog.of(Locale.US, Map.of("friends.add", "Added {player}.", "friends.remove", "Removed {player}.", "old.key", "Old"));
        var de = TranslationCatalog.of(Locale.GERMANY, Map.of("friends.add", "Hinzugefügt {username}."));
        var options = LocalizationScanOptions.defaults(Locale.US, Set.of(Locale.US, Locale.GERMANY, Locale.FRANCE));
        var report = LocalizationScanner.scan(refs, Map.of(Locale.US, en, Locale.GERMANY, de), options);
        assertTrue(report.failsBuild());
        assertTrue(report.findings().stream().anyMatch(f -> f.category() == LocalizationFinding.Category.MISSING_TRANSLATION && f.fallbackAvailable()));
        assertTrue(report.findings().stream().anyMatch(f -> f.category() == LocalizationFinding.Category.MISSING_FALLBACK && f.key().equals("friends.remove")));
        assertTrue(report.findings().stream().anyMatch(f -> f.category() == LocalizationFinding.Category.PLACEHOLDER_MISMATCH && f.locale().orElse("").equals("de-DE")));
        assertTrue(report.findings().stream().anyMatch(f -> f.category() == LocalizationFinding.Category.UNUSED_TRANSLATION));
        assertEquals(report.findings(), LocalizationScanner.scan(refs, Map.of(Locale.US, en, Locale.GERMANY, de), options).findings());
    }
    @Test void detectsDuplicateDeclarationsEvenWhenFallbackCanRecover() {
        TextCatalog refs = TextCatalog.builder().add(TextRef.translated("same.key", "First"), "a", "one")
                .add(TextRef.translated("same.key", "Second"), "b", "two").build();
        var report = LocalizationScanner.scan(refs, Map.of(Locale.US, TranslationCatalog.of(Locale.US, Map.of("same.key", "Same"))),
                LocalizationScanOptions.defaults(Locale.US, Set.of(Locale.US)));
        assertTrue(report.findings().stream().anyMatch(f -> f.category() == LocalizationFinding.Category.DUPLICATE_REFERENCE));
    }
    @Test void ignoredKeysSuppressConflictingReferenceDeclarations() {
        TextCatalog refs = TextCatalog.builder().add(TextRef.translated("legacy.key", "First"), "a", "one")
                .add(TextRef.translated("legacy.key", "Second"), "b", "two").build();
        var options = new LocalizationScanOptions(Locale.US, Set.of(),
                Set.of(LocalizationFinding.Category.DUPLICATE_REFERENCE), Set.of("legacy.key"));

        var report = LocalizationScanner.scan(refs, Map.of(), options);

        assertFalse(report.failsBuild());
        assertTrue(report.findings().stream().noneMatch(finding -> finding.key().equals("legacy.key")));
    }
    @Test void detectsMalformedResourcesInvalidLocalesAndDuplicateProperties() throws IOException {
        Files.writeString(temp.resolve("en-US.properties"), "key=first\nkey=second\n");
        Files.writeString(temp.resolve("not_a_locale.properties"), "x=y\n");
        Files.writeString(temp.resolve("de-DE.properties"), "bad=\\u12GZ\n");
        var loaded = TranslationResourceLoader.load(temp);
        assertEquals(1, loaded.catalogs().size());
        assertTrue(loaded.findings().stream().anyMatch(f -> f.message().contains("Duplicate translation key")));
        assertTrue(loaded.findings().stream().anyMatch(f -> f.message().contains("Invalid locale")));
        assertTrue(loaded.findings().stream().anyMatch(f -> f.message().contains("Malformed translation resource")));
    }
    @Test void generatedReferenceManifestRoundTripsMetadataAndConflicts() throws IOException {
        TextCatalog original = TextCatalog.builder().add(TextRef.translated("hello", "Hi"), "command", "root")
                .add(TextRef.translated("hello", "Hallo"), "help", "help-page").build();
        Path manifest = temp.resolve("refs.json"); TextCatalogManifest.write(original, manifest);
        TextCatalog read = TextCatalogManifest.read(manifest);
        assertEquals(original.entries(), read.entries());
        assertEquals(original.conflicts(), read.conflicts());
    }
    @Test void reportJsonHasStableMachineReadableCountsAndFindings() throws IOException {
        var report = LocalizationScanner.scan(TextCatalog.builder().add(TextRef.translated("missing"), "test", "source").build(),
                Map.of(), LocalizationScanOptions.defaults(Locale.US, Set.of(Locale.US)));
        String json = LocalizationReportJson.write(report);
        assertTrue(json.contains("\"failsBuild\" : true"));
        assertTrue(json.contains("MISSING_FALLBACK"));
        assertTrue(report.counts().containsKey("error"));
        assertFalse(report.counts().containsKey("ERROR"));
        assertEquals(json, LocalizationReportJson.write(report));
    }

    @Test void comparesPlaceholdersWhenBaseLocaleTranslationIsMissing() {
        TextCatalog references = TextCatalog.builder()
                .add(TextRef.translated("welcome", "Hello {player}"), "notification", "welcome")
                .build();
        var german = TranslationCatalog.of(Locale.GERMANY, Map.of("welcome", "Hallo {player}"));
        var french = TranslationCatalog.of(Locale.FRANCE, Map.of("welcome", "Bonjour {username}"));
        var options = new LocalizationScanOptions(Locale.US, Set.of(Locale.US, Locale.GERMANY, Locale.FRANCE),
                Set.of(LocalizationFinding.Category.PLACEHOLDER_MISMATCH), Set.of());

        var report = LocalizationScanner.scan(references,
                Map.of(Locale.GERMANY, german, Locale.FRANCE, french), options);

        assertTrue(report.findings().stream().anyMatch(finding ->
                finding.category() == LocalizationFinding.Category.PLACEHOLDER_MISMATCH
                        && finding.locale().orElseThrow().equals("fr-FR")));
    }

    @Test void resourceFindingsFollowConfiguredStrictness() throws IOException {
        Files.writeString(temp.resolve("en-US.properties"), "key=first\nkey=second\n");
        Files.writeString(temp.resolve("de-DE.properties"), "bad=\\u12GZ\n");
        var loaded = TranslationResourceLoader.load(temp);
        TextCatalog references = TextCatalog.builder().build();

        var warningOnly = LocalizationScanner.scan(references, loaded,
                new LocalizationScanOptions(Locale.US, Set.of(), Set.of(), Set.of()));
        assertFalse(warningOnly.failsBuild());
        assertTrue(warningOnly.findings().stream().allMatch(finding ->
                finding.severity() != LocalizationFinding.Severity.ERROR));

        var strict = LocalizationScanner.scan(references, loaded,
                new LocalizationScanOptions(Locale.US, Set.of(),
                        Set.of(LocalizationFinding.Category.INVALID_RESOURCE), Set.of()));
        assertTrue(strict.failsBuild());
        assertTrue(strict.findings().stream().filter(finding ->
                finding.category() == LocalizationFinding.Category.INVALID_RESOURCE).allMatch(finding ->
                finding.severity() == LocalizationFinding.Severity.ERROR));
    }
}
