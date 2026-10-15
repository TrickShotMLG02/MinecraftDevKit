package com.trickshotmlg.devkit.gradle.localization;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/** Registers the reusable translation validation task. */
public final class LocalizationScanPlugin implements Plugin<Project> {
    @Override public void apply(Project project) {
        LocalizationScanExtension extension = project.getExtensions().create("devkitLocalizationScan", LocalizationScanExtension.class);
        project.getTasks().register("scanDevKitTranslations", ScanDevKitTranslationsTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Checks localization catalogs against declared MinecraftDevKit text references.");
            task.getTranslationsDirectory().set(extension.getTranslationsDirectory());
            task.getReferenceManifest().set(extension.getReferenceManifest());
            task.getBaseLocale().set(extension.getBaseLocale());
            task.getRequiredLocales().set(extension.getRequiredLocales());
            task.getFailOn().set(extension.getFailOn());
            task.getReportFile().set(project.getLayout().getBuildDirectory().file("reports/minecraftdevkit/localization.json"));
        });
    }
}
