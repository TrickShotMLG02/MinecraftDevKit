package com.trickshotmlg.devkit.config.yaml;

import com.trickshotmlg.devkit.config.ConfigKey;
import com.trickshotmlg.devkit.config.ConfigSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test void concurrentUpdateDuringSerializationRemainsDirtyAfterTheCapturedGenerationIsWritten() throws Exception {
        CountDownLatch encoding = new CountDownLatch(1);
        CountDownLatch continueEncoding = new CountDownLatch(1);
        AtomicBoolean block = new AtomicBoolean();
        var codec = com.trickshotmlg.devkit.config.ConfigCodecs.of("blocking integer", raw -> {
            if (raw instanceof Number number) return number.intValue();
            throw new IllegalArgumentException("expected integer");
        }, value -> {
            if (block.get() && value == 1) {
                encoding.countDown();
                try { if (!continueEncoding.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("timed out"); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
            }
            return value;
        });
        var value = com.trickshotmlg.devkit.config.ConfigKey.builder("value", codec).defaultValue(1).build();
        var schema = ConfigSchema.builder().register(value).build();
        Path file = temp.resolve("concurrent.yml");
        YamlConfigHandle handle = YamlConfigHandle.createIfMissing(file, schema);
        handle.store().set(value, 1); // establish dirty state without an effective change
        block.set(true);
        var result = new java.util.concurrent.atomic.AtomicReference<YamlSaveResult>();
        Thread saving = new Thread(() -> result.set(handle.save(YamlSaveOptions.safeRewrite())));
        saving.start();
        assertTrue(encoding.await(3, TimeUnit.SECONDS));
        handle.store().set(value, 2);
        continueEncoding.countDown();
        saving.join(3000);
        assertFalse(saving.isAlive());
        assertTrue(result.get().success(), result.get().error().orElse(""));
        assertTrue(handle.store().isDirty());
        assertTrue(result.get().currentGeneration() > result.get().savedGeneration());
        assertTrue(Files.readString(file).contains("value: 1"));
        assertEquals(2, handle.store().get(value));
    }

    @Test void sensitiveCodecFailureDuringSaveDoesNotExposeTheValueOrReplaceTheFile() throws Exception {
        String secret = "private-token-7f9a";
        AtomicBoolean rejectEncoding = new AtomicBoolean();
        var codec = com.trickshotmlg.devkit.config.ConfigCodecs.of("credential", raw -> {
            if (!(raw instanceof String text)) throw new IllegalArgumentException("expected string");
            return text;
        }, value -> {
            if (rejectEncoding.get() && value.equals(secret)) throw new IllegalArgumentException("cannot encode " + value);
            return value;
        });
        var key = com.trickshotmlg.devkit.config.ConfigKey.builder("database.password", codec)
                .defaultValue("safe").sensitive().build();
        Path file = temp.resolve("secret.yml");
        YamlConfigHandle handle = YamlConfigHandle.createIfMissing(file,
                ConfigSchema.builder().register(key).build());
        String original = Files.readString(file);
        handle.store().set(key, secret);
        rejectEncoding.set(true);

        YamlSaveResult result = handle.save(YamlSaveOptions.safeRewrite());

        assertFalse(result.success());
        assertFalse(result.error().orElse("").contains(secret));
        assertEquals(original, Files.readString(file));
        assertTrue(handle.store().isDirty());
    }
}
