package com.trickshotmlg.devkit.config.yaml;

import com.trickshotmlg.devkit.config.ConfigKey;
import com.trickshotmlg.devkit.config.ConfigSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class YamlReloadTest {
    @TempDir Path temp;
    private final ConfigKey<Integer> MAX = ConfigKey.integer("friends.max", 100).range(1, 1000).build();
    private final ConfigKey<Boolean> ENABLED = ConfigKey.bool("friends.enabled", true).build();
    private final ConfigSchema schema = ConfigSchema.builder().register(MAX, ENABLED).build();

    @Test void failedReloadRollsBackAndDirtyStateRequiresExplicitDiscard() throws Exception {
        Path file = temp.resolve("config.yml");
        YamlConfigHandle handle = YamlConfigHandle.createIfMissing(file, schema);
        handle.store().set(MAX, 200);
        Files.writeString(file, "friends:\n  max: 250\n  enabled: false\n");
        YamlReloadResult dirty = handle.reload();
        assertFalse(dirty.success());
        assertTrue(dirty.conflict());
        assertEquals(200, handle.store().get(MAX));
        YamlReloadResult replaced = handle.reload(YamlReloadPolicy.DISCARD_PENDING_CHANGES);
        assertTrue(replaced.success());
        assertEquals(250, handle.store().get(MAX));
        assertFalse(handle.store().get(ENABLED));
        assertFalse(handle.store().isDirty());
        Files.writeString(file, "friends:\n  max: [malformed\n");
        YamlReloadResult failed = handle.reload();
        assertFalse(failed.success());
        assertEquals(250, handle.store().get(MAX));
        assertFalse(handle.store().get(ENABLED));
    }

    @Test void reloadAsyncUsesCallerExecutorAndHandleRemainsLive() throws Exception {
        Path file = temp.resolve("config.yml");
        YamlConfigHandle handle = YamlConfigHandle.createIfMissing(file, schema);
        var storeReference = handle.store();
        Files.writeString(file, "friends:\n  max: 333\n  enabled: true\n");
        var executor = Executors.newSingleThreadExecutor();
        try {
            YamlReloadResult result = handle.reloadAsync(YamlReloadPolicy.REJECT_IF_DIRTY, executor)
                    .toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertTrue(result.success());
            assertSame(storeReference, handle.store());
            assertEquals(333, storeReference.get(MAX));
        } finally { executor.shutdownNow(); }
    }

    @Test void watcherDebouncesFileReplacementAndStopsCleanly() throws Exception {
        Path file = temp.resolve("config.yml");
        YamlConfigHandle handle = YamlConfigHandle.createIfMissing(file, schema);
        CountDownLatch changed = new CountDownLatch(1);
        handle.startWatching(Duration.ofMillis(100), result -> { if (result.success() && handle.store().get(MAX) == 411) changed.countDown(); },
                failure -> fail(failure));
        Path replacement = temp.resolve("replacement.yml");
        Files.writeString(replacement, "friends:\n  max: 411\n  enabled: false\n");
        Files.move(replacement, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertTrue(changed.await(5, TimeUnit.SECONDS));
        handle.close();
        assertDoesNotThrow(() -> handle.startWatching(Duration.ofMillis(1), ignored -> { }, ignored -> { }));
        handle.stopWatching();
    }
}
