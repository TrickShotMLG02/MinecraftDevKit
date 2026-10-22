package com.trickshotmlg.devkit.config.yaml;

import com.trickshotmlg.devkit.config.ConfigKey;
import com.trickshotmlg.devkit.config.ConfigSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class YamlPersistenceTest {
    @TempDir Path temp;
    private final ConfigKey<Integer> MAX = ConfigKey.integer("friends.max", 100).build();
    private final ConfigKey<String> HOST = ConfigKey.string("database.host", "localhost").build();
    private final ConfigSchema schema = ConfigSchema.builder().register(MAX, HOST).build();

    @Test void persistsAtomicallyAndMarksOnlyWrittenGenerationClean() throws Exception {
        Path file = temp.resolve("config.yml");
        YamlConfigHandle handle = YamlConfigHandle.createIfMissing(file, schema);
        assertFalse(handle.store().isDirty());
        handle.store().set(MAX, 225);
        assertTrue(handle.store().isDirty());
        YamlSaveResult result = handle.save(YamlSaveOptions.safeRewrite());
        assertTrue(result.success(), result.error().orElse(""));
        assertTrue(result.written());
        assertFalse(handle.store().isDirty());
        assertTrue(Files.readString(file).contains("max: 225"));
        assertEquals(0, Files.list(temp).filter(path -> path.getFileName().toString().endsWith(".tmp")).count());
    }

    @Test void refusesToClobberExternalEditsAndAllowsExplicitOverwrite() throws Exception {
        Path file = temp.resolve("config.yml");
        YamlConfigHandle handle = YamlConfigHandle.createIfMissing(file, schema);
        handle.store().set(MAX, 200);
        Files.writeString(file, "friends:\n  max: 150\ndatabase:\n  host: external\nplugin-extra: retained\n");
        YamlSaveResult conflict = handle.save(YamlSaveOptions.safeRewrite());
        assertFalse(conflict.success());
        assertTrue(conflict.conflict());
        assertTrue(Files.readString(file).contains("150"));
        YamlSaveOptions overwrite = new YamlSaveOptions(YamlSaveOptions.ExternalChangePolicy.OVERWRITE,
                YamlSaveOptions.DocumentPolicy.REWRITE_DOCUMENT);
        YamlSaveResult result = handle.save(overwrite);
        assertTrue(result.success(), result.error().orElse(""));
        assertTrue(Files.readString(file).contains("200"));
        assertFalse(Files.readString(file).contains("plugin-extra"));
    }

    @Test void retainsUnknownFieldsOnSaveAndNeverOverwritesOnCreate() throws Exception {
        Path file = temp.resolve("config.yml");
        Files.writeString(file, "friends:\n  max: 120\ndatabase:\n  host: db.local\nfuture:\n  option: [a, b]\n");
        YamlConfigHandle handle = YamlConfigHandle.load(file, schema);
        assertEquals(1, handle.unknownValues().size());
        assertFalse(new YamlConfigFile(schema).createDefaults(file));
        handle.store().set(MAX, 130);
        YamlSaveResult result = handle.save(YamlSaveOptions.safeRewrite());
        assertTrue(result.success(), result.error().orElse(""));
        String saved = Files.readString(file);
        assertTrue(saved.contains("option:"));
        assertTrue(saved.contains("- a"));
        assertTrue(saved.contains("- b"));
    }
}
