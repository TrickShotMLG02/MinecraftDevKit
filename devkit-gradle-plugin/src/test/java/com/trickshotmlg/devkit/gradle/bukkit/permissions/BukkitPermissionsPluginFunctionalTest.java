package com.trickshotmlg.devkit.gradle.bukkit.permissions;

import com.trickshotmlg.devkit.permissions.PermissionCatalog;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitPermissionsPluginFunctionalTest {
    private static final String PLUGIN_ID = "com.trickshotmlg.devkit.bukkit-permissions";
    private static final String CATALOG_CLASS = "sample.permissions.FriendPermissions";

    @TempDir
    Path temporaryDirectory;

    @Test
    void mergesCatalogIntoPluginYmlAndPackagesExactlyOneDescriptorInJar() throws IOException {
        Path project = consumerProject(""
                + "name: FriendNet\n"
                + "version: '1.0.0'\n"
                + "main: sample.FriendNetPlugin\n"
                + "api-version: '1.21'\n"
                + "commands:\n"
                + "  friends:\n"
                + "    description: Manage friends\n");

        BuildResult result = run(project, "clean", "jar");

        assertNotNull(result.task(":compileJava"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateBukkitPermissions").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":processResources").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());

        Map<String, Object> descriptor = loadYaml(Files.readString(project.resolve(
                "build/generated/minecraftdevkit/resources/plugin.yml"), StandardCharsets.UTF_8));
        assertEquals("FriendNet", descriptor.get("name"));
        assertEquals("sample.FriendNetPlugin", descriptor.get("main"));
        assertEquals("1.21", descriptor.get("api-version"));
        assertTrue(map(descriptor.get("commands")).containsKey("friends"));

        Map<String, Object> permissions = map(descriptor.get("permissions"));
        assertTrue(permissions.containsKey("friendnet.friends.list"));
        assertFalse(permissions.containsKey("friendnet.basic"));
        Map<String, Object> defaultGroup = map(permissions.get("friendnet.default"));
        assertTrue(map(defaultGroup.get("children")).containsKey("friendnet.friends.list"));
        Map<String, Object> moderator = map(permissions.get("friendnet.moderator"));
        assertFalse(map(moderator.get("children")).containsKey("friendnet.friends.requests.accept.all"));
        assertEquals("name: FriendNet\nversion: '1.0.0'\nmain: sample.FriendNetPlugin\napi-version: '1.21'\n"
                        + "commands:\n  friends:\n    description: Manage friends\n",
                Files.readString(project.resolve("src/main/resources/plugin.yml")));

        try (JarFile jar = new JarFile(project.resolve("build/libs/sample.jar").toFile())) {
            List<String> descriptors = jar.stream().map(entry -> entry.getName())
                    .filter("plugin.yml"::equals).toList();
            assertEquals(List.of("plugin.yml"), descriptors);
            Map<String, Object> jarDescriptor = loadYaml(new String(
                    jar.getInputStream(jar.getEntry("plugin.yml")).readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("FriendNet", jarDescriptor.get("name"));
            assertTrue(map(jarDescriptor.get("permissions")).containsKey("friendnet.admin"));
        }
    }

    @Test
    void generationIsIncrementalAndCatalogChangesRegenerateTheResource() throws IOException {
        Path project = consumerProject("name: FriendNet\nversion: '1.0'\nmain: sample.Main\n");

        BuildResult first = run(project, "generateBukkitPermissions");
        assertEquals(TaskOutcome.SUCCESS, first.task(":generateBukkitPermissions").getOutcome());

        BuildResult unchanged = run(project, "generateBukkitPermissions");
        assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":generateBukkitPermissions").getOutcome());

        Path pluginYml = project.resolve("src/main/resources/plugin.yml");
        Files.writeString(pluginYml, "name: FriendNet\nversion: '1.1'\nmain: sample.Main\n");
        BuildResult descriptorChanged = run(project, "generateBukkitPermissions");
        assertEquals(TaskOutcome.SUCCESS, descriptorChanged.task(":generateBukkitPermissions").getOutcome());
        assertEquals("1.1", loadYaml(Files.readString(project.resolve(
                "build/generated/minecraftdevkit/resources/plugin.yml"))).get("version"));

        Path source = project.resolve("src/main/java/sample/permissions/FriendPermissions.java");
        String original = Files.readString(source);
        Files.writeString(source, original.replace("public static final Permission FRIEND_LIST =",
                "public static final Permission FRIEND_EXTRA = NS.permission(\"friends.extra\");\n"
                        + "    public static final Permission FRIEND_LIST ="));

        BuildResult changed = run(project, "generateBukkitPermissions");
        assertEquals(TaskOutcome.SUCCESS, changed.task(":compileJava").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, changed.task(":generateBukkitPermissions").getOutcome());
        assertTrue(map(loadYaml(Files.readString(project.resolve(
                "build/generated/minecraftdevkit/resources/plugin.yml")))
                .get("permissions")).containsKey("friendnet.friends.extra"));
    }

    @Test
    void cleanRebuildHasNoCircularTaskDependencies() throws IOException {
        Path project = consumerProject(null);

        BuildResult result = run(project, "clean", "build");

        assertEquals(TaskOutcome.SUCCESS, result.task(":compileJava").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateBukkitPermissions").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":processResources").getOutcome());
        assertTrue(Files.exists(project.resolve("build/resources/main/plugin.yml")));
    }

    @Test
    void taskSupportsConfigurationCacheAndReusesItsOutput() throws IOException {
        Path project = consumerProject(null);

        BuildResult first = run(project, "generateBukkitPermissions", "--configuration-cache");
        BuildResult second = run(project, "generateBukkitPermissions", "--configuration-cache");

        assertEquals(TaskOutcome.SUCCESS, first.task(":generateBukkitPermissions").getOutcome());
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateBukkitPermissions").getOutcome());
        assertTrue(second.getOutput().contains("Reusing configuration cache"));
    }

    @Test
    void missingCatalogClassProducesActionableFailure() throws IOException {
        Path project = consumerProject(null, "sample.permissions.DoesNotExist", "CATALOG");

        BuildResult result = runAndFail(project, "generateBukkitPermissions");

        assertTrue(result.getOutput().contains("Permission catalog class 'sample.permissions.DoesNotExist' was not found"));
    }

    @Test
    void missingCatalogFieldProducesActionableFailure() throws IOException {
        Path project = consumerProject(null, CATALOG_CLASS, "NO_CATALOG");

        BuildResult result = runAndFail(project, "generateBukkitPermissions");

        assertTrue(result.getOutput().contains("has no public field named 'NO_CATALOG'"));
    }

    @Test
    void invalidCatalogFieldTypeProducesActionableFailure() throws IOException {
        Path project = consumerProject(null);
        Path source = project.resolve("src/main/java/sample/permissions/FriendPermissions.java");
        Files.writeString(source, "package sample.permissions; public final class FriendPermissions {"
                + " public static final String CATALOG = \"wrong\"; }");

        BuildResult result = runAndFail(project, "generateBukkitPermissions");

        assertTrue(result.getOutput().contains("must have type PermissionCatalog"));
    }

    @Test
    void catalogInitializationFailureIsReportedClearly() throws IOException {
        Path project = consumerProject(null);
        Path source = project.resolve("src/main/java/sample/permissions/FriendPermissions.java");
        Files.writeString(source, "package sample.permissions; import com.trickshotmlg.devkit.permissions.*;"
                + " public final class FriendPermissions {"
                + " public static PermissionCatalog fail() { throw new IllegalStateException(\"catalog boom\"); }"
                + " public static final PermissionCatalog CATALOG = fail(); }");

        BuildResult result = runAndFail(project, "generateBukkitPermissions");

        assertTrue(result.getOutput().contains("Could not initialize permission catalog "
                + "sample.permissions.FriendPermissions.CATALOG"));
    }

    @Test
    void malformedYamlAndPermissionCollisionsFailInsteadOfOverwriting() throws IOException {
        Path malformed = consumerProject("name: FriendNet\npermissions: [unterminated\n");
        BuildResult malformedResult = runAndFail(malformed, "generateBukkitPermissions");
        assertTrue(malformedResult.getOutput().contains("Could not parse base plugin descriptor"));

        Path collision = consumerProject("name: FriendNet\npermissions:\n  friendnet.friends.list:\n    default: op\n");
        BuildResult collisionResult = runAndFail(collision, "generateBukkitPermissions");
        assertTrue(collisionResult.getOutput().contains("declared both in the source plugin.yml and the Java catalog"));
    }

    @Test
    void invalidTopLevelYamlStructureIsRejected() throws IOException {
        Path project = consumerProject("- not-a-plugin-map\n- invalid-root\n");

        BuildResult result = runAndFail(project, "generateBukkitPermissions");

        assertTrue(result.getOutput().contains("must contain a YAML mapping at the document root"));
    }

    private Path consumerProject(String pluginYml) throws IOException {
        return consumerProject(pluginYml, CATALOG_CLASS, "CATALOG");
    }

    private Path consumerProject(String pluginYml, String catalogClass, String catalogField) throws IOException {
        Path project = temporaryDirectory.resolve("consumer-" + System.nanoTime());
        Files.createDirectories(project.resolve("src/main/java/sample/permissions"));
        Files.createDirectories(project.resolve("src/main/resources"));
        Files.writeString(project.resolve("settings.gradle.kts"), "rootProject.name = \"sample\"\n");
        Files.writeString(project.resolve("build.gradle.kts"), buildScript(catalogClass, catalogField));
        Files.writeString(project.resolve("src/main/java/sample/permissions/FriendPermissions.java"), friendPermissionsSource());
        if (pluginYml != null) {
            Files.writeString(project.resolve("src/main/resources/plugin.yml"), pluginYml);
        }
        return project;
    }

    private String buildScript(String catalogClass, String catalogField) throws IOException {
        Path permissionClasses;
        try {
            permissionClasses = Path.of(PermissionCatalog.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI());
        } catch (java.net.URISyntaxException failure) {
            throw new IOException("Could not locate permission framework classes for the TestKit fixture", failure);
        }
        String classPath = permissionClasses.toString().replace("\\", "/").replace("\"", "\\\"");
        return "plugins { java; id(\"" + PLUGIN_ID + "\") }\n"
                + "minecraftDevKit { bukkitPermissions { catalogClass.set(\"" + catalogClass + "\"); "
                + "catalogField.set(\"" + catalogField + "\") } }\n"
                + "dependencies { implementation(files(\"" + classPath + "\")) }\n";
    }

    private static String friendPermissionsSource() {
        return """
                package sample.permissions;
                import com.trickshotmlg.devkit.permissions.*;
                public final class FriendPermissions {
                    private static final PermissionNamespace NS = PermissionNamespace.create("friendnet");
                    public static final Permission FRIEND_LIST = NS.permission("friends.list");
                    public static final Permission FRIEND_LIST_OTHERS = FRIEND_LIST.child("others");
                    public static final Permission FRIEND_ADD = NS.permission("friends.add");
                    public static final Permission FRIEND_REMOVE = NS.permission("friends.remove");
                    public static final Permission REQUEST_ACCEPT = NS.permission("friends.requests.accept");
                    public static final Permission REQUEST_ACCEPT_ALL = REQUEST_ACCEPT.child("all");
                    public static final PermissionSection BASIC = NS.section("basic", FRIEND_LIST, FRIEND_ADD, FRIEND_REMOVE);
                    public static final PermissionSection EXTENDED = NS.section("extended", FRIEND_LIST_OTHERS, REQUEST_ACCEPT_ALL);
                    public static final PermissionGroup DEFAULT = NS.group("default", group -> group
                            .defaultAccess(DefaultAccess.TRUE).grant(BASIC));
                    public static final PermissionGroup STAFF = NS.group("staff", group -> group
                            .grant(REQUEST_ACCEPT));
                    public static final PermissionGroup MODERATOR = NS.group("moderator", group -> group
                            .inherits(DEFAULT, STAFF).grant(EXTENDED).exclude(REQUEST_ACCEPT_ALL));
                    public static final PermissionGroup ADMIN = NS.group("admin", group -> group
                            .defaultAccess(DefaultAccess.OP).grantAllPermissions());
                    public static final PermissionCatalog CATALOG = NS.build();
                }
                """;
    }

    private BuildResult run(Path project, String... arguments) {
        return GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .forwardOutput()
                .build();
    }

    private BuildResult runAndFail(Path project, String... arguments) {
        return GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .forwardOutput()
                .buildAndFail();
    }

    private static Map<String, Object> loadYaml(String text) {
        Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        assertTrue(loaded instanceof Map<?, ?>, "YAML root must be a mapping");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) loaded;
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertTrue(value instanceof Map<?, ?>, "Expected YAML mapping but got " + value);
        return (Map<String, Object>) value;
    }
}
