package com.trickshotmlg.devkit.examples.integration;

import com.trickshotmlg.devkit.bukkit.gui.PageInfo;
import com.trickshotmlg.devkit.config.yaml.YamlConfigHandle;
import com.trickshotmlg.devkit.config.yaml.YamlReloadPolicy;
import com.trickshotmlg.devkit.config.yaml.YamlSaveOptions;
import com.trickshotmlg.devkit.lifecycle.LifecycleManager;
import com.trickshotmlg.devkit.lifecycle.ComponentKey;
import com.trickshotmlg.devkit.lifecycle.LifecycleException;
import com.trickshotmlg.devkit.lifecycle.ServiceKey;
import com.trickshotmlg.devkit.localization.LocalizationService;
import com.trickshotmlg.devkit.localization.yaml.YamlTranslationSource;
import com.trickshotmlg.devkit.scheduler.TaskExecutionTarget;
import com.trickshotmlg.devkit.scheduler.TaskSchedulers;
import com.trickshotmlg.devkit.scheduler.TaskScope;
import com.trickshotmlg.devkit.scheduler.TickDelay;
import com.trickshotmlg.devkit.scheduler.testing.ManualTaskBackend;
import com.trickshotmlg.devkit.text.TextRef;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Cross-module checks for the public contracts used by the Paper application. */
class IntegrationFrameworkTest {
    @TempDir Path temporary;

    @Test void configurationRejectsInvalidReloadAndExternalOverwrite() throws Exception {
        Path file = temporary.resolve("config.yml");
        try (YamlConfigHandle handle = YamlConfigHandle.createIfMissing(file, IntegrationConfig.SCHEMA)) {
            assertEquals(40, handle.store().get(IntegrationConfig.REFRESH_INTERVAL));
            handle.store().set(IntegrationConfig.NOTIFICATIONS, false);
            assertTrue(handle.store().isDirty());
            Files.writeString(file, "config-version: 1\nfeatures:\n  notifications-enabled: true\n  show-online-count: true\ngui:\n  refresh-interval-ticks: 2\n");
            var save = handle.save(YamlSaveOptions.safeRewrite());
            assertFalse(save.success());
            assertTrue(save.conflict());
            assertFalse(handle.store().get(IntegrationConfig.NOTIFICATIONS));
            assertEquals(40, handle.store().get(IntegrationConfig.REFRESH_INTERVAL));

            handle.store().set(IntegrationConfig.NOTIFICATIONS, true);
            var invalid = handle.reload(YamlReloadPolicy.DISCARD_PENDING_CHANGES);
            assertFalse(invalid.success());
            assertEquals(40, handle.store().get(IntegrationConfig.REFRESH_INTERVAL));
        }
    }

    @Test void localizationResolvesGermanAndFallsBackToEnglish() {
        try (LocalizationService service = LocalizationService.builder()
                .source(YamlTranslationSource.bundled("lang", "en", "de"))
                .defaultFallbackLocale(Locale.ENGLISH).defaultFallbackEnabled(true).build()) {
            var loaded = service.reload();
            assertTrue(loaded.successful(), loaded.error().orElse("unknown localization failure"));
            assertEquals(Locale.GERMAN, service.resolveWithTrace(
                    TextRef.translated("integration.menu.title"), Locale.GERMAN, java.util.Map.of()).resolvedLocale().orElseThrow());
            assertEquals(Locale.ENGLISH, service.resolveWithTrace(
                    TextRef.translated("integration.menu.title"), Locale.FRENCH, java.util.Map.of()).resolvedLocale().orElseThrow());
        }
    }

    @Test void translationReloadPublishesExternalOverrides() throws Exception {
        Path directory = temporary.resolve("lang");
        Files.createDirectories(directory);
        try (LocalizationService service = LocalizationService.builder()
                .source(YamlTranslationSource.bundled("lang", "en", "de"))
                .source(YamlTranslationSource.directory(directory))
                .defaultFallbackLocale(Locale.ENGLISH).defaultFallbackEnabled(true).build()) {
            assertTrue(service.reload().successful());
            assertEquals("MinecraftDevKit-Integration", ((TextComponent) service.resolve(
                    TextRef.translated("integration.menu.title"), Locale.GERMAN)).content());
            Files.writeString(directory.resolve("de.yml"), "integration:\n  menu:\n    title: Benutzerdefiniert\n");
            assertTrue(service.reload().successful());
            var translation = service.resolve(TextRef.translated("integration.menu.title"), Locale.GERMAN);
            assertEquals("Benutzerdefiniert", ((TextComponent) translation).content());
        }
    }

    @Test void lifecycleClosureCancelsItsOwnedTaskScope() {
        ManualTaskBackend backend = new ManualTaskBackend();
        var scheduler = TaskSchedulers.create(backend);
        ServiceKey<TaskScope> scopeKey = ServiceKey.of("integration-task-scope", TaskScope.class);
        AtomicBoolean ran = new AtomicBoolean();
        LifecycleManager lifecycle = LifecycleManager.builder()
                .service(scopeKey, context -> {
                    TaskScope scope = context.manage(scheduler.scope());
                    scope.sync().later(TickDelay.of(20), () -> ran.set(true));
                    return scope;
                }).build();
        lifecycle.start();
        assertEquals(1, backend.pendingTaskCount());
        lifecycle.close();
        backend.advanceTicks(20);
        backend.runDue(TaskExecutionTarget.SYNC);
        assertFalse(ran.get());
        assertEquals(0, backend.pendingTaskCount());
        scheduler.close();
    }

    @Test void startupRollbackCancelsResourcesAcquiredBeforeLaterServiceFailure() {
        ManualTaskBackend backend = new ManualTaskBackend();
        var scheduler = TaskSchedulers.create(backend);
        ServiceKey<TaskScope> scopeKey = ServiceKey.of("rollback-task-scope", TaskScope.class);
        AtomicBoolean ran = new AtomicBoolean();
        LifecycleManager lifecycle = LifecycleManager.builder()
                .service(scopeKey, context -> {
                    TaskScope scope = context.manage(scheduler.scope());
                    scope.sync().later(TickDelay.of(5), () -> ran.set(true));
                    return scope;
                })
                .component(ComponentKey.of("intentional-startup-failure"), context -> {
                    throw new IllegalStateException("expected integration rollback");
                }, null, scopeKey)
                .build();
        assertThrows(LifecycleException.class, lifecycle::start);
        backend.advanceTicks(5);
        backend.runDue(TaskExecutionTarget.SYNC);
        assertFalse(ran.get());
        assertEquals(0, backend.pendingTaskCount());
        scheduler.close();
    }

    @Test void paginationClampsEmptyAndShrunkDatasets() {
        assertEquals(1, PageInfo.of(4, 0, 10).totalPages());
        PageInfo last = PageInfo.of(3, 25, 10);
        assertEquals(3, last.currentPage());
        assertEquals(5, last.visibleItemCount());
        PageInfo shrunk = PageInfo.of(last.currentPage(), 15, 10);
        assertEquals(2, shrunk.currentPage());
        assertFalse(shrunk.hasNext());
    }
}
