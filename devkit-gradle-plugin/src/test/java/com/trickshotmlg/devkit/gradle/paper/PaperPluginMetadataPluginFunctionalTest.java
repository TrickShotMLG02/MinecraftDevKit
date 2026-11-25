package com.trickshotmlg.devkit.gradle.paper;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import com.trickshotmlg.devkit.permissions.PermissionCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class PaperPluginMetadataPluginFunctionalTest {
    private static final String PLUGIN_ID = "com.trickshotmlg.devkit.bukkit-permissions";

    @TempDir Path temporaryDirectory;

    @Test
    void emitsOnlyNativePaperDescriptorAndSerializesBothDependencyPhases() throws IOException {
        Path project = project(true);
        BuildResult result = run(project, "clean", "jar");

        assertEquals(TaskOutcome.SKIPPED, result.task(":generateBukkitPermissions").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":generatePaperPluginMetadata").getOutcome());
        assertTrue(Files.exists(project.resolve("build/resources/main/paper-plugin.yml")));
        assertFalse(Files.exists(project.resolve("build/resources/main/plugin.yml")));

        Map<String, Object> descriptor = read(project.resolve("build/generated/minecraftdevkit/resources/paper-plugin.yml"));
        assertEquals("NativeFixture", descriptor.get("name"));
        assertEquals("fixture.Main", descriptor.get("main"));
        assertEquals("1.21.11", descriptor.get("api-version"));
        assertEquals("fixture.Bootstrap", descriptor.get("bootstrapper"));
        assertEquals(List.of("A. Author", "B. Author"), descriptor.get("authors"));
        Map<String, Object> dependencies = map(descriptor.get("dependencies"));
        Map<String, Object> bootstrap = map(dependencies.get("bootstrap"));
        Map<String, Object> server = map(dependencies.get("server"));
        assertEquals(Map.of("load", "BEFORE", "required", true, "join-classpath", false),
                map(bootstrap.get("RegistryPlugin")));
        assertEquals(Map.of("load", "AFTER", "required", false, "join-classpath", false),
                map(server.get("OptionalRuntime")));

        try (JarFile jar = new JarFile(project.resolve("build/libs/native-fixture-1.0.0.jar").toFile())) {
            assertEquals(1, jar.stream().filter(entry -> entry.getName().equals("paper-plugin.yml")).count());
            assertEquals(0, jar.stream().filter(entry -> entry.getName().equals("plugin.yml")).count());
        }
    }

    @Test
    void outputIsStableAndTaskCanReuseConfigurationCache() throws IOException {
        Path project = project(false);
        BuildResult first = run(project, "generatePaperPluginMetadata", "--configuration-cache");
        String output = Files.readString(project.resolve("build/generated/minecraftdevkit/resources/paper-plugin.yml"));
        BuildResult second = run(project, "generatePaperPluginMetadata", "--configuration-cache");

        assertEquals(TaskOutcome.SUCCESS, first.task(":generatePaperPluginMetadata").getOutcome());
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generatePaperPluginMetadata").getOutcome());
        assertTrue(second.getOutput().contains("Reusing configuration cache"));
        assertEquals(output, Files.readString(project.resolve("build/generated/minecraftdevkit/resources/paper-plugin.yml")));
    }

    @Test
    void bukkitDescriptorModeRemainsTheDefaultAndPaperGenerationIsSkipped() throws IOException {
        Path project = project(false, "", null);
        Files.writeString(project.resolve("src/main/resources/plugin.yml"),
                "name: BukkitFixture\nversion: '1.0'\nmain: fixture.Main\n");
        BuildResult result = run(project, "clean", "processResources");
        assertEquals(TaskOutcome.SKIPPED, result.task(":generatePaperPluginMetadata").getOutcome());
        assertTrue(Files.exists(project.resolve("build/resources/main/plugin.yml")));
        assertFalse(Files.exists(project.resolve("build/resources/main/paper-plugin.yml")));
    }

    @Test
    void switchingDescriptorModeNeverPackagesAStaleOppositeDescriptor() throws IOException {
        Path project = project(false, "", "BUKKIT");
        run(project, "processResources");
        Path resources = project.resolve("build/resources/main");
        assertTrue(Files.exists(resources.resolve("plugin.yml")));
        assertFalse(Files.exists(resources.resolve("paper-plugin.yml")));

        Path buildFile = project.resolve("build.gradle.kts");
        String buildScript = Files.readString(buildFile);
        Files.writeString(buildFile, buildScript.replace("PluginDescriptorPlatform.BUKKIT", "PluginDescriptorPlatform.PAPER_NATIVE"));
        run(project, "processResources");
        assertFalse(Files.exists(resources.resolve("plugin.yml")));
        assertTrue(Files.exists(resources.resolve("paper-plugin.yml")));

        Files.writeString(buildFile, Files.readString(buildFile)
                .replace("PluginDescriptorPlatform.PAPER_NATIVE", "PluginDescriptorPlatform.BUKKIT"));
        run(project, "processResources");
        assertTrue(Files.exists(resources.resolve("plugin.yml")));
        assertFalse(Files.exists(resources.resolve("paper-plugin.yml")));
    }

    @Test
    void rejectsSelfDependenciesAndDuplicateEntries() throws IOException {
        Path self = project(false, "serverDependency(\"NativeFixture\") { }\n");
        assertTrue(runFail(self, "generatePaperPluginMetadata").getOutput().contains("cannot depend on itself"));

        Path duplicate = project(false, "serverDependency(\"OtherPlugin\") { }\nserverDependency(\"otherplugin\") { }\n");
        assertTrue(runFail(duplicate, "generatePaperPluginMetadata").getOutput().contains("duplicate server dependency"));
    }

    @Test
    void rejectsInvalidMainAndDependencyNames() throws IOException {
        Path invalidMain = project(false, "mainClass.set(\"NotQualified\")\n");
        assertTrue(runFail(invalidMain, "generatePaperPluginMetadata").getOutput().contains("fully-qualified Java class"));

        Path invalidDependency = project(false, "serverDependency(\"bad plugin name\") { }\n");
        assertTrue(runFail(invalidDependency, "generatePaperPluginMetadata").getOutput().contains("invalid Paper dependency name"));
    }

    private Path project(boolean dependencies) throws IOException { return project(dependencies, "", "PAPER_NATIVE"); }

    private Path project(boolean dependencies, String extraDsl) throws IOException {
        return project(dependencies, extraDsl, "PAPER_NATIVE");
    }

    private Path project(boolean dependencies, String extraDsl, String platform) throws IOException {
        Path project = temporaryDirectory.resolve("paper-consumer-" + System.nanoTime());
        Files.createDirectories(project.resolve("src/main/java"));
        Files.createDirectories(project.resolve("src/main/resources"));
        Files.writeString(project.resolve("settings.gradle.kts"), "rootProject.name = \"native-fixture\"\n");
        String dependencyDsl = dependencies
                ? "bootstrapDependency(\"RegistryPlugin\") { load.set(PaperDependencyLoadOrder.BEFORE) }\n"
                + "serverDependency(\"OptionalRuntime\") { load.set(PaperDependencyLoadOrder.AFTER); required.set(false) }\n"
                : "";
        String platformDsl = platform == null ? "" : "platform.set(PluginDescriptorPlatform.%s)\n".formatted(platform);
        String bukkitCatalogDsl = "";
        String dependencyBlock = "";
        if (platform == null || "BUKKIT".equals(platform)) {
            bukkitCatalogDsl = "bukkitPermissions { catalogClass.set(\"fixture.Permissions\") }\n";
            final Path permissionClasses;
            try {
                permissionClasses = Path.of(PermissionCatalog.class.getProtectionDomain().getCodeSource()
                        .getLocation().toURI());
            } catch (java.net.URISyntaxException failure) {
                throw new IOException("Could not locate permission API classes for TestKit", failure);
            }
            dependencyBlock = "dependencies { implementation(files(\""
                    + permissionClasses.toString().replace("\\", "/") + "\")) }\n";
            Files.createDirectories(project.resolve("src/main/java/fixture"));
            Files.writeString(project.resolve("src/main/java/fixture/Permissions.java"), """
                    package fixture;
                    import com.trickshotmlg.devkit.permissions.*;
                    public final class Permissions {
                        private static final PermissionNamespace NS = PermissionNamespace.create("fixture");
                        public static final PermissionCatalog CATALOG = NS.build();
                    }
                    """);
        }
        Files.writeString(project.resolve("build.gradle.kts"), """
                import com.trickshotmlg.devkit.gradle.paper.PluginDescriptorPlatform
                import com.trickshotmlg.devkit.gradle.paper.PaperDependencyLoadOrder
                plugins { java; id("%s") }
                version = "1.0.0"
                %s
                minecraftDevKit {
                    %s
                    %s
                    paperPlugin {
                        name.set("NativeFixture")
                        mainClass.set("fixture.Main")
                        apiVersion.set("1.21.11")
                        bootstrapper.set("fixture.Bootstrap")
                        authors.set(listOf("A. Author", "B. Author"))
                        %s%s
                    }
                }
                """.formatted(PLUGIN_ID, dependencyBlock, platformDsl, bukkitCatalogDsl, dependencyDsl, extraDsl));
        // Both source descriptors deliberately conflict with generated output and must be excluded.
        Files.writeString(project.resolve("src/main/resources/plugin.yml"), "name: stale-bukkit\n");
        Files.writeString(project.resolve("src/main/resources/paper-plugin.yml"), "name: stale-paper\n");
        return project;
    }

    private BuildResult run(Path project, String... args) {
        return GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments(args).forwardOutput().build();
    }

    private BuildResult runFail(Path project, String... args) {
        return GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments(args).forwardOutput().buildAndFail();
    }

    private static Map<String, Object> read(Path file) throws IOException {
        Object value = new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file, StandardCharsets.UTF_8));
        assertInstanceOf(Map.class, value);
        return map(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertInstanceOf(Map.class, value);
        return (Map<String, Object>) new LinkedHashMap<>((Map<?, ?>) value);
    }
}
