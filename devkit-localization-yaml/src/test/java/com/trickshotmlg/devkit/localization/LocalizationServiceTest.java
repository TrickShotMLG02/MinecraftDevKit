package com.trickshotmlg.devkit.localization;

import com.trickshotmlg.devkit.localization.yaml.YamlTranslationSource;
import com.trickshotmlg.devkit.text.MissingTranslationPolicy;
import com.trickshotmlg.devkit.text.TextProvider;
import com.trickshotmlg.devkit.text.TextRef;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationServiceTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    @TempDir Path temp;

    @Test
    void successfulReloadChangesResolutionThroughTheSameProviderReference() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("lang"));
        Path english = folder.resolve("en.yml");
        Files.writeString(english, "hello: \"Hello {player}\"\n");
        LocalizationService service = service(folder);
        try {
            ReloadResult initial = service.reload();
            assertTrue(initial.successful());
            assertTrue(initial.snapshotChanged());
            assertEquals(1, initial.translationCount());
            TextProvider reference = service;
            assertEquals("Hello Alex", PLAIN.serialize(reference.resolve(TextRef.translated("hello"), Locale.US,
                    Map.of("player", "Alex"))));

            Files.writeString(english, "hello: \"Welcome {player}\"\n");
            ReloadResult next = service.reload();
            assertTrue(next.successful());
            assertTrue(next.snapshotChanged());
            assertEquals("Welcome Alex", PLAIN.serialize(reference.resolve(TextRef.translated("hello"), Locale.US,
                    Map.of("player", "Alex"))));
            ReloadResult unchanged = service.reload();
            assertTrue(unchanged.successful());
            assertFalse(unchanged.snapshotChanged());
        } finally { service.close(); }
    }

    @Test
    void malformedReloadKeepsTheLastGoodSnapshotActive() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("lang"));
        Path english = folder.resolve("en.yml");
        Files.writeString(english, "hello: \"Old value\"\n");
        LocalizationService service = service(folder);
        try {
            assertTrue(service.reload().successful());
            long generation = service.generation();
            Files.writeString(english, "hello: [broken\n");
            ReloadResult failed = service.reload();
            assertFalse(failed.successful());
            assertFalse(failed.snapshotChanged());
            assertEquals(generation, failed.generation());
            assertTrue(failed.error().orElseThrow().contains("Invalid translation YAML"));
            assertEquals("Old value", resolve(service, "hello", "en-US"));
        } finally { service.close(); }
    }

    @Test
    void asyncReloadIsSerializedAndUsesTheSameAtomicPipeline() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("lang"));
        Files.writeString(folder.resolve("en.yml"), "hello: \"Hello\"\n");
        LocalizationService service = service(folder);
        try {
            ReloadResult result = service.reloadAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertTrue(result.successful());
            assertEquals("Hello", resolve(service, "hello", "en-US"));
        } finally { service.close(); }
    }

    @Test
    void fallbackConfigurationDistinguishesDefaultLocaleFromTextRefFallback() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("lang"));
        Files.writeString(folder.resolve("en.yml"), "hello: \"Hello\"\n");
        LocalizationService service = LocalizationService.builder()
                .source(YamlTranslationSource.directory(folder))
                .defaultFallbackLocale(Locale.ENGLISH).defaultFallbackEnabled(true)
                .missingKeyPolicy(MissingTranslationPolicy.SHOW_MARKER).build();
        try {
            assertTrue(service.reload().successful());
            assertEquals("Hello", resolve(service, "hello", "de-AT"));
            assertEquals(TextResolution.Fallback.DEFAULT_LOCALE, service.resolveWithTrace(
                    TextRef.translated("hello"), Locale.forLanguageTag("de-AT"), Map.of()).fallback());
            assertEquals("backup", PLAIN.serialize(service.resolve(TextRef.translated("missing", "backup"),
                    Locale.GERMAN)));
            assertEquals("<missing:missing.no.fallback>", resolve(service, "missing.no.fallback", "de"));
        } finally { service.close(); }
    }

    @Test
    void trustedCatalogMarkupRendersButPlaceholderMarkupRemainsPlainText() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("markup"));
        Files.writeString(folder.resolve("en.yml"), "message: \"<red>Hello {player}</red>\"\n");
        LocalizationService service = service(folder);
        try {
            assertTrue(service.reload().successful());
            var result = service.resolve(TextRef.translated("message"), Locale.ENGLISH,
                    Map.of("player", "<green>Steve</green>"));
            assertEquals("Hello <green>Steve</green>", PLAIN.serialize(result));
            assertEquals(NamedTextColor.RED, result.color());
        } finally { service.close(); }
    }

    @Test
    void failedLaterSourceDoesNotPublishPartialCandidate() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("lang"));
        Files.writeString(folder.resolve("en.yml"), "hello: \"Stable\"\n");
        AtomicInteger loads = new AtomicInteger();
        TranslationSource failsOnReload = new TranslationSource() {
            @Override public Map<Locale, TranslationCatalog> load() throws IOException {
                if (loads.incrementAndGet() > 1) throw new IOException("secondary source failed");
                return Map.of();
            }
        };
        LocalizationService service = LocalizationService.builder()
                .source(YamlTranslationSource.directory(folder)).source(failsOnReload).build();
        try {
            assertTrue(service.reload().successful());
            Files.writeString(folder.resolve("en.yml"), "hello: \"Unpublished\"\n");
            ReloadResult failed = service.reload();
            assertFalse(failed.successful());
            assertEquals("Stable", resolve(service, "hello", "en"));
        } finally { service.close(); }
    }

    @Test
    void readersSeeOldSnapshotUntilACompleteCandidateIsPublished() throws Exception {
        AtomicReference<String> value = new AtomicReference<>("Old");
        CountDownLatch loadingSecondSnapshot = new CountDownLatch(1);
        CountDownLatch continueLoad = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        TranslationSource source = new TranslationSource() {
            @Override public Map<Locale, TranslationCatalog> load() throws IOException {
                if (loads.incrementAndGet() == 2) {
                    loadingSecondSnapshot.countDown();
                    try {
                        if (!continueLoad.await(5, TimeUnit.SECONDS)) throw new IOException("test load timed out");
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException(exception);
                    }
                }
                return Map.of(Locale.ENGLISH, TranslationCatalog.of(Locale.ENGLISH,
                        Map.of("message", value.get())));
            }
        };
        LocalizationService service = LocalizationService.builder().source(source).build();
        try {
            assertTrue(service.reload().successful());
            value.set("New");
            var reload = service.reloadAsync();
            assertTrue(loadingSecondSnapshot.await(5, TimeUnit.SECONDS));
            assertEquals("Old", resolve(service, "message", "en"));
            continueLoad.countDown();
            assertTrue(reload.toCompletableFuture().get(5, TimeUnit.SECONDS).successful());
            assertEquals("New", resolve(service, "message", "en"));
        } finally {
            continueLoad.countDown();
            service.close();
        }
    }

    @Test
    void filesystemWatcherReloadsChangedYamlAndCanBeStopped() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("lang"));
        Path file = folder.resolve("en.yml");
        Files.writeString(file, "hello: \"Before\"\n");
        LocalizationService service = service(folder);
        try {
            assertTrue(service.reload().successful());
            service.startWatching(Duration.ofMillis(100));
            Files.writeString(file, "hello: \"After\"\n");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (System.nanoTime() < deadline && !resolve(service, "hello", "en").equals("After")) {
                Thread.sleep(25);
            }
            assertEquals("After", resolve(service, "hello", "en"));
            long generation = service.generation();
            service.stopWatching();
            Files.writeString(file, "hello: \"Stopped\"\n");
            Thread.sleep(400);
            assertEquals(generation, service.generation());
            assertEquals("After", resolve(service, "hello", "en"));
        } finally { service.close(); }
    }

    @Test
    void watcherDetectsNewLocaleSubdirectoryAndYamlFileCreation() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("lang"));
        LocalizationService service = service(folder);
        try {
            assertTrue(service.reload().successful());
            service.startWatching(Duration.ofMillis(80));
            Path german = Files.createDirectories(folder.resolve("de"));
            Files.writeString(german.resolve("friends.yml"), "hello: \"Hallo\"\n");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (System.nanoTime() < deadline && !resolve(service, "hello", "de-AT").equals("Hallo")) {
                Thread.sleep(25);
            }
            assertEquals("Hallo", resolve(service, "hello", "de-AT"));
        } finally { service.close(); }
    }

    @Test
    void watcherReloadsDeletionButIgnoresUnrelatedFiles() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("watch-delete"));
        Path file = folder.resolve("en.yml");
        Files.writeString(file, "hello: \"Present\"\n");
        LocalizationService service = service(folder);
        try {
            assertTrue(service.reload().successful());
            service.startWatching(Duration.ofMillis(80));
            long before = service.generation();
            Files.writeString(folder.resolve("notes.txt"), "not a translation");
            Thread.sleep(300);
            assertEquals(before, service.generation());

            Files.delete(file);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (System.nanoTime() < deadline && service.generation() == before) Thread.sleep(25);
            assertTrue(service.generation() > before);
            assertEquals("hello", resolve(service, "hello", "en"));
        } finally { service.close(); }
    }

    @Test
    void watcherReloadsWhenLocaleDirectoryIsRemoved() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("watch-directory-delete"));
        Path german = Files.createDirectories(folder.resolve("de"));
        Files.writeString(german.resolve("friends.yml"), "hello: \"Hallo\"\n");
        LocalizationService service = service(folder);
        try {
            assertTrue(service.reload().successful());
            assertEquals("Hallo", resolve(service, "hello", "de"));
            service.startWatching(Duration.ofMillis(80));
            long before = service.generation();

            Files.move(german, temp.resolve("removed-de"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (System.nanoTime() < deadline && service.generation() == before) Thread.sleep(25);

            assertTrue(service.generation() > before);
            assertEquals("hello", resolve(service, "hello", "de"));
        } finally { service.close(); }
    }

    @Test
    void separateServicesRemainIndependent() throws Exception {
        Path one = Files.createDirectories(temp.resolve("one"));
        Path two = Files.createDirectories(temp.resolve("two"));
        Files.writeString(one.resolve("en.yml"), "hello: \"One\"\n");
        Files.writeString(two.resolve("en.yml"), "hello: \"Two\"\n");
        LocalizationService a = service(one), b = service(two);
        try {
            assertTrue(a.reload().successful()); assertTrue(b.reload().successful());
            assertNotEquals(resolve(a, "hello", "en"), resolve(b, "hello", "en"));
        } finally { a.close(); b.close(); }
    }

    private static LocalizationService service(Path directory) {
        return LocalizationService.builder().source(YamlTranslationSource.directory(directory))
                .defaultFallbackLocale(Locale.ENGLISH).defaultFallbackEnabled(true).build();
    }

    private static String resolve(LocalizationService service, String key, String locale) {
        return PLAIN.serialize(service.resolve(TextRef.translated(key), Locale.forLanguageTag(locale)));
    }
}
