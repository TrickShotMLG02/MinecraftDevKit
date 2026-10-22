package com.trickshotmlg.devkit.localization.yaml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlTranslationSourceTest {
    @TempDir Path temp;

    @Test
    void parsesNestedUtf8MappingsToSortedDotKeys() throws Exception {
        Map<String, String> parsed = YamlCatalogParser.parse(stream("""
                greeting: "Grüß dich"
                friends:
                  add:
                    success: "Added {player}."
                """), "memory.yml");
        assertEquals(Map.of("greeting", "Grüß dich", "friends.add.success", "Added {player}."), parsed);
    }

    @Test
    void combinesLocaleRootFileAndNestedFilesWithStableKeys() throws Exception {
        Files.writeString(temp.resolve("en.yml"), "shared: \"root\"\n");
        Files.createDirectories(temp.resolve("en"));
        Files.writeString(temp.resolve("en/commands.yml"), "commands:\n  list: \"List\"\n");
        Files.writeString(temp.resolve("en/friends.yml"), "friends:\n  add: \"Add\"\n");
        var loaded = YamlTranslationSource.directory(temp).load();
        assertEquals(Map.of("shared", "root", "commands.list", "List", "friends.add", "Add"),
                loaded.get(Locale.ENGLISH).translations());
    }

    @Test
    void rejectsDuplicateKeysWithinYamlAndAcrossFiles() throws Exception {
        assertThrows(IOException.class, () -> YamlCatalogParser.parse(stream("a: one\na: two\n"), "duplicate.yml"));
        Files.createDirectories(temp.resolve("en"));
        Files.writeString(temp.resolve("en/one.yml"), "a: one\n");
        Files.writeString(temp.resolve("en/two.yml"), "a: two\n");
        IOException error = assertThrows(IOException.class, () -> YamlTranslationSource.directory(temp).load());
        assertTrue(error.getMessage().contains("Duplicate translation key 'a'"));
    }

    @Test
    void rejectsNonStringScalarsTagsAndExcessiveNesting() {
        assertThrows(IOException.class, () -> YamlCatalogParser.parse(stream("flag: true\n"), "scalar.yml"));
        assertThrows(IOException.class, () -> YamlCatalogParser.parse(stream("value: !!java/object {}\n"), "tag.yml"));
        assertThrows(IOException.class, () -> YamlCatalogParser.parse(stream("a:\n  b:\n    c:\n      d:\n        e:\n          f:\n            g:\n              h:\n                i:\n                  j:\n                    k:\n                      l:\n                        m:\n                          n:\n                            o:\n                              p:\n                                q:\n                                  r:\n                                    s:\n                                      t:\n                                        u:\n                                          v:\n                                            w:\n                                              x:\n                                                y:\n                                                  z:\n                                                    aa:\n                                                      ab:\n                                                        ac:\n                                                          ad:\n                                                            ae:\n                                                              af:\n                                                                ag:\n                                                                  ah:\n                                                                    ai:\n                                                                      aj:\n                                                                        ak:\n                                                                          al:\n                                                                            am:\n                                                                              an:\n                                                                                ao:\n                                                                                  ap:\n                                                                                    aq:\n                                                                                      ar:\n                                                                                        as:\n                                                                                          at:\n                                                                                            au:\n                                                                                              av:\n                                                                                                aw:\n                                                                                                  ax:\n                                                                                                    ay:\n                                                                                                      az:\n                                                                                                        leaf: \"x\"\n"), "deep.yml"));
    }

    @Test
    void rejectsRecursiveYamlAliasesWithoutRecursingDuringFlattening() {
        IOException error = assertThrows(IOException.class, () -> YamlCatalogParser.parse(stream("root: &root\n  child: *root\n"),
                "recursive.yml"));
        assertTrue(error.getMessage().contains("Cyclic YAML aliases"));
    }

    @Test
    void limitsAliasesAndDocumentSize() {
        StringBuilder aliases = new StringBuilder("base: &base {message: \"Hello\"}\n");
        for (int index = 0; index < 24; index++) aliases.append("copy").append(index).append(": *base\n");
        assertThrows(IOException.class, () -> YamlCatalogParser.parse(stream(aliases.toString()), "aliases.yml"));
        assertThrows(IOException.class, () -> YamlCatalogParser.parse(stream("key: \"" + "x".repeat(1_048_577)
                + "\"\n"), "large.yml"));
    }

    @Test
    void bundledSourcesLoadRootFilesAndExplicitMultiFileIndexes() throws Exception {
        var source = YamlTranslationSource.bundled(getClass().getClassLoader(), "yaml-fixture", "en", "fr");
        var catalogs = source.load();
        assertEquals("Hello", catalogs.get(Locale.ENGLISH).translations().get("greeting"));
        assertEquals(Map.of("greeting", "Bonjour", "menu.open", "Ouvrir"),
                catalogs.get(Locale.FRENCH).translations());
    }

    @Test
    void bundledIndexRejectsRepeatedFileNamesEvenWhenTheFileHasNoKeys() throws Exception {
        Path resources = Files.createDirectories(temp.resolve("duplicate-index/fr"));
        Files.writeString(resources.resolve("index.txt"), "empty.yml\nempty.yml\n");
        Files.writeString(resources.resolve("empty.yml"), "{}\n");
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{temp.toUri().toURL()}, null)) {
            IOException error = assertThrows(IOException.class, () -> YamlTranslationSource.bundled(loader,
                    "duplicate-index", "fr").load());
            assertTrue(error.getMessage().contains("Duplicate YAML filename"));
        }
    }

    @Test
    void rejectsSamePriorityRootAndDirectoryCollisionsAndMalformedLocales() throws Exception {
        Files.writeString(temp.resolve("en.yml"), "a: root\n");
        Files.createDirectories(temp.resolve("en"));
        Files.writeString(temp.resolve("en/commands.yml"), "a: nested\n");
        assertThrows(IOException.class, () -> YamlTranslationSource.directory(temp).load());
        assertThrows(IllegalArgumentException.class, () -> YamlTranslationSource.bundled(
                getClass().getClassLoader(), "translations", "not_a_locale"));
    }

    @Test
    void absentExternalDirectoryIsAnEmptySourceForInitialInstallation() throws Exception {
        var loaded = YamlTranslationSource.directory(temp.resolve("not-created")).load();
        assertTrue(loaded.isEmpty());
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
