package com.trickshotmlg.devkit.config.yaml;

import com.trickshotmlg.devkit.config.ConfigKey;
import com.trickshotmlg.devkit.config.ConfigSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YamlConfigFileTest {
    @TempDir Path temp;
    private final ConfigKey<Integer> MAX = ConfigKey.integer("friends.max", 100).range(1, 1000).build();
    private final ConfigKey<Boolean> ENABLED = ConfigKey.bool("friends.requests.enabled", true).build();
    private final ConfigKey<Duration> TIMEOUT = ConfigKey.duration("friends.requests.timeout", Duration.ofMinutes(5)).build();
    private final ConfigSchema schema = ConfigSchema.builder().register(MAX, ENABLED, TIMEOUT).build();

    @Test void loadsNestedValuesAndPreservesUnknownSettingsWithWarnings() throws Exception {
        Path file = temp.resolve("config.yml");
        Files.writeString(file, "friends:\n  max: 250\n  requests:\n    enabled: false\n    timeout: PT2M\nextra:\n  keep-me: [one, two]\n");
        YamlConfigLoadResult result = new YamlConfigFile(schema).load(file);
        assertEquals(250, result.store().get(MAX));
        assertFalse(result.store().get(ENABLED));
        assertEquals(Duration.ofMinutes(2), result.store().get(TIMEOUT));
        assertTrue(result.unknownValues().containsKey("extra.keep-me"));
        assertEquals(1, result.issues().size());
        String saved = new YamlConfigFile(schema).serialize(result.store(), result.unknownValues());
        assertTrue(saved.contains("keep-me"));
        assertTrue(saved.contains("PT2M"));
    }

    @Test void rejectsDuplicateKeysInvalidTypesAndUnknownStrictMode() throws Exception {
        Path file = temp.resolve("bad.yml");
        Files.writeString(file, "friends:\n  max: 1\n  max: 2\n");
        assertThrows(java.io.IOException.class, () -> new YamlConfigFile(schema).load(file));
        Files.writeString(file, "friends:\n  max: nope\n");
        assertThrows(YamlConfigException.class, () -> new YamlConfigFile(schema).load(file));
        Files.writeString(file, "unknown: true\n");
        assertThrows(YamlConfigException.class, () -> new YamlConfigFile(schema, UnknownKeyPolicy.REJECT).load(file));
    }

    @Test void generatedDefaultsAreNestedDeterministicAndNeverOverwrite() throws Exception {
        YamlConfigFile yaml = new YamlConfigFile(schema);
        Path file = temp.resolve("generated/config.yml");
        assertTrue(yaml.createDefaults(file));
        String first = Files.readString(file);
        assertTrue(first.contains("max: 100"));
        assertTrue(first.contains("timeout: PT5M"));
        Files.writeString(file, "administrator value\n");
        assertFalse(yaml.createDefaults(file));
        assertEquals("administrator value\n", Files.readString(file));
        assertEquals(first, yaml.defaultsYaml());
    }

    @Test void rejectsRecursiveAliasesAndUnsupportedYamlTags() throws Exception {
        Path file = temp.resolve("recursive.yml");
        Files.writeString(file, "loop: &loop [*loop]\n");
        assertThrows(java.io.IOException.class, () -> new YamlConfigFile(schema).load(file));
        Files.writeString(file, "x: !!java/object:java.lang.Runtime { }\n");
        assertThrows(java.io.IOException.class, () -> new YamlConfigFile(schema).load(file));
    }
}
