package com.trickshotmlg.devkit.gradle.localization;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.ListProperty;

import javax.inject.Inject;

/** Gradle configuration for the translation validation task. */
public abstract class LocalizationScanExtension {
    @Inject public LocalizationScanExtension(ProjectLayout layout) {
        getTranslationsDirectory().convention(layout.getProjectDirectory().dir("src/main/resources/translations"));
        getReferenceManifest().convention(layout.getProjectDirectory().file("src/main/localization/text-references.json"));
        getBaseLocale().convention("en-US");
        getRequiredLocales().convention(java.util.List.of("en-US"));
        getFailOn().convention(java.util.List.of("MISSING_FALLBACK", "PLACEHOLDER_MISMATCH", "INVALID_RESOURCE", "DUPLICATE_REFERENCE"));
    }
    public abstract DirectoryProperty getTranslationsDirectory();
    public abstract RegularFileProperty getReferenceManifest();
    public abstract org.gradle.api.provider.Property<String> getBaseLocale();
    public abstract ListProperty<String> getRequiredLocales();
    public abstract ListProperty<String> getFailOn();
}
