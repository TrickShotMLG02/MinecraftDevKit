package com.trickshotmlg.devkit.config.yaml;

import com.trickshotmlg.devkit.config.ConfigKey;
import com.trickshotmlg.devkit.config.ConfigSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConfigMigrationTest {
    @TempDir Path temp;
    private final ConfigKey<Integer> MAX = ConfigKey.integer("friends.max", 100).build();
    private final ConfigSchema schema = ConfigSchema.builder().version(2).register(MAX).build();
    private final ConfigMigrationRegistry migrations = ConfigMigrationRegistry.builder()
            .register(1, 2, values -> values.move("friends.max-count", "friends.max"))
            .build();

    @Test void migratesOrderedValuesAndRequiresExplicitBackupBeforePersistence() throws Exception {
        Path file = temp.resolve("config.yml");
        Files.writeString(file, "config-version: 1\nfriends:\n  max-count: 275\n");
        YamlConfigHandle handle = YamlConfigHandle.load(file, schema, UnknownKeyPolicy.PRESERVE_AND_WARN, migrations);
        assertEquals(275, handle.store().get(MAX));
        assertTrue(handle.store().isDirty());
        YamlSaveResult saved = handle.save(YamlSaveOptions.safeRewrite());
        assertTrue(saved.success(), saved.error().orElse(""));
        assertFalse(handle.store().isDirty());
        assertTrue(Files.readString(file).contains("config-version: 2"));
        assertTrue(Files.exists(temp.resolve("config.yml.v1.bak")));
        YamlConfigHandle reloaded = YamlConfigHandle.load(file, schema);
        assertEquals(275, reloaded.store().get(MAX));
        assertFalse(reloaded.store().isDirty());
    }

    @Test void rejectsMissingMigrationsFutureVersionsAndMigrationCollisions() throws Exception {
        Path file = temp.resolve("config.yml");
        Files.writeString(file, "config-version: 1\nold: 5\n");
        assertThrows(YamlConfigException.class, () -> new YamlConfigFile(schema, UnknownKeyPolicy.PRESERVE,
                ConfigMigrationRegistry.builder().build()).load(file));
        Files.writeString(file, "config-version: 3\nfriends:\n  max: 4\n");
        assertThrows(YamlConfigException.class, () -> new YamlConfigFile(schema, UnknownKeyPolicy.PRESERVE, migrations).load(file));
        Files.writeString(file, "config-version: 1\nfriends:\n  max: 5\n  max-count: 6\n");
        YamlConfigException collision = assertThrows(YamlConfigException.class,
                () -> new YamlConfigFile(schema, UnknownKeyPolicy.PRESERVE, migrations).load(file));
        assertTrue(collision.getMessage().contains("migration"));
    }

    @Test void migrationMustNotPartiallyModifyAnythingWhenItThrows() throws Exception {
        Path file = temp.resolve("config.yml");
        Files.writeString(file, "config-version: 1\nfriends:\n  max-count: 99\n");
        ConfigMigrationRegistry failing = ConfigMigrationRegistry.builder().register(1, 2, values -> {
            values.set("friends.max", 88);
            throw new IllegalStateException("stop");
        }).build();
        assertThrows(YamlConfigException.class, () -> new YamlConfigFile(schema, UnknownKeyPolicy.PRESERVE, failing).load(file));
        assertTrue(Files.readString(file).contains("max-count: 99"));
    }
}
