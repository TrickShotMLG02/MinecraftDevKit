package com.trickshotmlg.devkit.localization.cli;

import com.trickshotmlg.devkit.localization.TextCatalogManifest;
import com.trickshotmlg.devkit.text.TextCatalog;
import com.trickshotmlg.devkit.text.TextRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LocalizationScannerCliTest {
    @TempDir Path temp;
    @Test void emitsJsonAndFailsForMissingRequiredTranslation() throws Exception {
        Path translations = Files.createDirectory(temp.resolve("translations"));
        Files.writeString(translations.resolve("en-US.properties"), "unused=No reference\n");
        Path refs = temp.resolve("references.json");
        TextCatalogManifest.write(TextCatalog.builder().add(TextRef.translated("required.key"), "test", "fixture").build(), refs);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit = LocalizationScannerCli.run(new String[]{"--translations", translations.toString(), "--references", refs.toString(), "--format", "json"},
                new PrintStream(stdout), new PrintStream(stderr));
        assertEquals(1, exit, stderr.toString());
        assertTrue(stdout.toString().contains("MISSING_FALLBACK"));
    }
    @Test void helpAndInvalidArgumentsHaveDocumentedExitCodes() {
        assertEquals(0, LocalizationScannerCli.run(new String[]{"--help"}, System.out, System.err));
        assertEquals(2, LocalizationScannerCli.run(new String[]{"--bogus", "x"}, System.out, new PrintStream(new ByteArrayOutputStream())));
    }

    @Test void scansYamlWithLocaleFallbackAndCanFailSpecificallyOnGlobalFallback() throws Exception {
        Path translations = Files.createDirectory(temp.resolve("yaml"));
        Files.writeString(translations.resolve("en.yml"), "messages:\n  hello: \"Hello\"\n");
        Files.writeString(translations.resolve("de.yml"), "messages:\n  other: \"Anders\"\n");
        Path refs = temp.resolve("yaml-refs.json");
        TextCatalogManifest.write(TextCatalog.builder().add(TextRef.translated("messages.hello"), "test", "hello")
                .build(), refs);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int exit = LocalizationScannerCli.run(new String[]{"--translations", translations.toString(),
                        "--references", refs.toString(), "--resource-format", "yaml", "--base-locale", "en",
                        "--locales", "de", "--fallback-locale", "en", "--fail-on", "DEFAULT_LOCALE_FALLBACK",
                        "--format", "json"}, new PrintStream(stdout), new PrintStream(new ByteArrayOutputStream()));

        assertEquals(1, exit);
        assertTrue(stdout.toString().contains("DEFAULT_LOCALE_FALLBACK"));
    }

    @Test void malformedYamlIsReturnedAsAnInvalidResourceFinding() throws Exception {
        Path translations = Files.createDirectory(temp.resolve("bad-yaml"));
        Files.writeString(translations.resolve("en.yml"), "messages: [broken\n");
        Path refs = temp.resolve("bad-refs.json");
        TextCatalogManifest.write(TextCatalog.builder().build(), refs);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int exit = LocalizationScannerCli.run(new String[]{"--translations", translations.toString(),
                        "--references", refs.toString(), "--resource-format", "yaml", "--format", "json"},
                new PrintStream(stdout), new PrintStream(new ByteArrayOutputStream()));

        assertEquals(1, exit);
        assertTrue(stdout.toString().contains("INVALID_RESOURCE"));
    }

    @Test void missingTranslationDirectoryIsReportedAsInvalidResource() throws Exception {
        Path refs = temp.resolve("missing-dir-refs.json");
        TextCatalogManifest.write(TextCatalog.builder().build(), refs);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int exit = LocalizationScannerCli.run(new String[]{"--translations", temp.resolve("absent").toString(),
                        "--references", refs.toString(), "--format", "json"},
                new PrintStream(stdout), new PrintStream(new ByteArrayOutputStream()));

        assertEquals(1, exit);
        assertTrue(stdout.toString().contains("INVALID_RESOURCE"));
        assertTrue(stdout.toString().contains("Translation directory does not exist"));
    }

    @Test void translationFileUsedAsDirectoryProducesOneInvalidResourceFinding() throws Exception {
        Path notDirectory = temp.resolve("translations.yml");
        Files.writeString(notDirectory, "hello: \"Hello\"\n");
        Path refs = temp.resolve("file-path-refs.json");
        TextCatalogManifest.write(TextCatalog.builder().build(), refs);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int exit = LocalizationScannerCli.run(new String[]{"--translations", notDirectory.toString(),
                        "--references", refs.toString(), "--resource-format", "yaml", "--format", "json"},
                new PrintStream(stdout), new PrintStream(new ByteArrayOutputStream()));

        String report = stdout.toString();
        assertEquals(1, exit);
        assertEquals(1, report.split("INVALID_RESOURCE", -1).length - 1);
        assertTrue(report.contains("Translation path is not a directory"));
    }
}
