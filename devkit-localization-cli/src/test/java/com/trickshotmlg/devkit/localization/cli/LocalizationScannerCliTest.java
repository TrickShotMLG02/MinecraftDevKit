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
}
