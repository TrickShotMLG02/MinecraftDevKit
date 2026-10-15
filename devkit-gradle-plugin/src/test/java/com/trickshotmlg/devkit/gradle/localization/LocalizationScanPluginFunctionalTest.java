package com.trickshotmlg.devkit.gradle.localization;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LocalizationScanPluginFunctionalTest {
    private static final String ID = "com.trickshotmlg.devkit.localization-scan";
    @TempDir Path temp;
    @Test void scansResourcesIncrementallyAndSupportsConfigurationCache() throws IOException {
        Path project = createProject("hello=Hello {name}\n");
        BuildResult first = run(project, "scanDevKitTranslations", "--configuration-cache");
        assertEquals(TaskOutcome.SUCCESS, first.task(":scanDevKitTranslations").getOutcome());
        assertTrue(Files.readString(project.resolve("build/reports/minecraftdevkit/localization.json")).contains("findings"));
        BuildResult second = run(project, "scanDevKitTranslations", "--configuration-cache");
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":scanDevKitTranslations").getOutcome());
        assertTrue(second.getOutput().contains("Reusing configuration cache"));
        Files.writeString(project.resolve("translations/en-US.properties"), "hello=Changed {name}\n");
        BuildResult changed = run(project, "scanDevKitTranslations");
        assertEquals(TaskOutcome.SUCCESS, changed.task(":scanDevKitTranslations").getOutcome());
    }
    @Test void strictFindingsFailBuildAfterWritingReport() throws IOException {
        Path project = createProject("other=Unused\n");
        BuildResult result = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("scanDevKitTranslations", "--stacktrace").buildAndFail();
        assertTrue(result.getOutput().contains("Localization validation failed"));
        assertTrue(Files.exists(project.resolve("build/reports/minecraftdevkit/localization.json")));
    }
    @Test void scansYamlAndReportsPartialRegionalCoverageAsInheritance() throws IOException {
        Path project = temp.resolve("yaml-localization-" + System.nanoTime());
        Files.createDirectories(project.resolve("translations/en-GB"));
        Files.writeString(project.resolve("settings.gradle.kts"), "rootProject.name = \"consumer\"\n");
        Files.writeString(project.resolve("build.gradle.kts"), """
                plugins { id("com.trickshotmlg.devkit.localization-scan") }
                devkitLocalizationScan {
                    translationsDirectory.set(layout.projectDirectory.dir("translations"))
                    referenceManifest.set(layout.projectDirectory.file("references.json"))
                    baseLocale.set("en")
                    defaultFallbackLocale.set("en")
                    requiredLocales.set(listOf("en", "en-GB"))
                    failOn.set(listOf("MISSING_FALLBACK", "INVALID_RESOURCE"))
                }
                """);
        Files.writeString(project.resolve("translations/en.yml"), "messages:\n  hello: \"Hello\"\n");
        Files.writeString(project.resolve("translations/en-GB/commands.yml"), "commands:\n  mate: \"Hi mate\"\n");
        Files.writeString(project.resolve("references.json"), """
                {"schemaVersion":1,"references":[{"key":"messages.hello","categories":["test"],"sources":["fixture"]}],"conflicts":[]}
                """);

        BuildResult result = run(project, "scanDevKitTranslations");

        assertEquals(TaskOutcome.SUCCESS, result.task(":scanDevKitTranslations").getOutcome());
        String report = Files.readString(project.resolve("build/reports/minecraftdevkit/localization.json"));
        assertTrue(report.contains("PARENT_LOCALE_FALLBACK"));
        assertFalse(report.contains("MISSING_FALLBACK"));
    }
    private Path createProject(String properties) throws IOException {
        Path project = temp.resolve("localization-" + System.nanoTime());
        Files.createDirectories(project.resolve("translations"));
        Files.writeString(project.resolve("settings.gradle.kts"), "rootProject.name = \"consumer\"\n");
        Files.writeString(project.resolve("build.gradle.kts"), """
                plugins { id("com.trickshotmlg.devkit.localization-scan") }
                devkitLocalizationScan {
                    translationsDirectory.set(layout.projectDirectory.dir("translations"))
                    referenceManifest.set(layout.projectDirectory.file("references.json"))
                    baseLocale.set("en-US")
                    requiredLocales.set(listOf("en-US"))
                    failOn.set(listOf("MISSING_TRANSLATION", "MISSING_FALLBACK", "PLACEHOLDER_MISMATCH"))
                }
                """);
        Files.writeString(project.resolve("translations/en-US.properties"), properties);
        Files.writeString(project.resolve("references.json"), """
                {"schemaVersion":1,"references":[{"key":"hello","fallback":"Hi {name}","categories":["test"],"sources":["fixture"]}],"conflicts":[]}
                """);
        return project;
    }
    private BuildResult run(Path project, String... args) {
        return GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath().withArguments(args).build();
    }
}
