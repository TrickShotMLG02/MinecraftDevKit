package com.trickshotmlg.devkit.gradle.localization;

import com.trickshotmlg.devkit.localization.LocalizationFinding;
import com.trickshotmlg.devkit.localization.LocalizationScanOptions;
import com.trickshotmlg.devkit.localization.LocalizationScanner;
import com.trickshotmlg.devkit.localization.LocalizationReportJson;
import com.trickshotmlg.devkit.localization.TextCatalogManifest;
import com.trickshotmlg.devkit.localization.TranslationResourceLoader;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Validates translation files against a generated or supplied text-reference manifest. */
@CacheableTask
public abstract class ScanDevKitTranslationsTask extends DefaultTask {
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE) public abstract DirectoryProperty getTranslationsDirectory();
    @InputFile @PathSensitive(PathSensitivity.RELATIVE) public abstract RegularFileProperty getReferenceManifest();
    @Input public abstract Property<String> getBaseLocale();
    @Input public abstract ListProperty<String> getRequiredLocales();
    @Input public abstract ListProperty<String> getFailOn();
    @OutputFile public abstract RegularFileProperty getReportFile();

    @TaskAction public void scan() {
        try {
            Set<Locale> locales = getRequiredLocales().get().stream().map(ScanDevKitTranslationsTask::locale)
                    .collect(java.util.stream.Collectors.toCollection(() -> new java.util.TreeSet<>(java.util.Comparator.comparing(Locale::toLanguageTag))));
            Locale base = locale(getBaseLocale().get()); locales.add(base);
            EnumSet<LocalizationFinding.Category> strict = EnumSet.noneOf(LocalizationFinding.Category.class);
            getFailOn().get().forEach(value -> strict.add(LocalizationFinding.Category.valueOf(value.toUpperCase(Locale.ROOT))));
            LocalizationScanOptions options = new LocalizationScanOptions(base, locales, strict, Set.of());
            var references = TextCatalogManifest.read(getReferenceManifest().get().getAsFile().toPath());
            var translations = TranslationResourceLoader.load(getTranslationsDirectory().get().getAsFile().toPath());
            var report = LocalizationScanner.scan(references, translations, options);
            var destination = getReportFile().get().getAsFile().toPath();
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, LocalizationReportJson.write(report), StandardCharsets.UTF_8);
            report.findings().forEach(finding -> getLogger().lifecycle("{} [{}] {}: {}", finding.severity(),
                    finding.locale().orElse("-"), finding.key(), finding.message()));
            if (report.failsBuild()) throw new GradleException("Localization validation failed; see " + destination);
        } catch (GradleException e) { throw e; }
        catch (Exception e) { throw new GradleException("Could not scan DevKit translations: " + e.getMessage(), e); }
    }
    private static Locale locale(String tag) { return new Locale.Builder().setLanguageTag(tag).build(); }
}
