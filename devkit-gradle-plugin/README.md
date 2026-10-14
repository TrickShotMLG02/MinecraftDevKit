# MinecraftDevKit Bukkit Permissions Gradle Plugin

The `devkit-gradle-plugin` module provides `com.trickshotmlg.devkit.bukkit-permissions`. It loads a compiled Java `PermissionCatalog`, exports its traditional Bukkit/Spigot `plugin.yml` permission metadata, and merges that metadata into the main resource descriptor during normal Gradle resource processing.

This plugin does not generate Paper `paper-plugin.yml`, command metadata, runtime registrations, or metadata for Velocity, Fabric, Forge, or NeoForge. It has no server runtime requirement.

## Publishing and applying locally

The plugin and marker artifacts are published to Maven Local with the repository's existing command:

```powershell
.\gradlew.bat publishToMavenLocal
```

```sh
./gradlew publishToMavenLocal
```

In a consuming project's `settings.gradle.kts`, add Maven Local to plugin resolution:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```

Then apply and configure the plugin in `build.gradle.kts`:

```kotlin
plugins {
    java
    id("com.trickshotmlg.devkit.bukkit-permissions") version "0.1.0-SNAPSHOT"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // Needed to compile the Java class that declares the catalog.
    implementation("com.trickshotmlg.devkit:devkit-permissions:0.1.0-SNAPSHOT")
}

minecraftDevKit {
    bukkitPermissions {
        catalogClass.set("com.example.plugin.permissions.PluginPermissions")
        // Optional; defaults to CATALOG.
        catalogField.set("CATALOG")
    }
}
```

The field must be public, static, and declared with type `PermissionCatalog`. Java source remains the permission definition source; the plugin discovers the compiled field and does not need a duplicate list or manually maintained generated YAML.

## Lifecycle and generated files

The plugin applies Gradle's Java plugin and registers `generateBukkitPermissions`. The task depends on `compileJava`, then reads main compiled classes and the main runtime dependency configuration. It deliberately does not include the main processed-resources output, so it does not depend on `processResources` and cannot form a cycle with that task.

`processResources` depends on generation. The task merges the generated mapping with `src/main/resources/plugin.yml` when that file exists, writes `build/generated/minecraftdevkit/resources/plugin.yml`, and excludes the original root `plugin.yml` from normal resource copying. Other resources continue through the usual Gradle resource pipeline. The JAR therefore receives one root `plugin.yml`, and the source descriptor is never modified. If no source descriptor exists, the generated file contains a root `permissions` mapping.

The generated permission declarations come from `BukkitPermissionYamlExporter`. Ordinary permission declarations and group nodes are emitted; sections are omitted. Inherited grants are flattened from `PermissionCatalog.effectivePermissions(group)`, exclusions are omitted from the corresponding group, and no false/negative Bukkit child relationship is generated. Catalog output order is retained.

## Catalog initialization and errors

The catalog class and its static field are initialized inside a Gradle process-isolated worker JVM, not the long-lived Gradle daemon classloader. The worker uses the compiled main output and runtime dependencies and closes its catalog classloader after generation.

Static catalog initialization executes user code during the build. Keep it lightweight and independent of a running Minecraft server, plugin lifecycle, and runtime-only services. Missing classes or fields, non-static or wrongly typed fields, initialization/linkage failures, malformed YAML, invalid top-level YAML structures, invalid permission mappings, and permission node collisions fail the task with an actionable message.

The task declares the catalog class/field, compiled runtime classpath, optional source descriptor, and generated output. It is incremental and cacheable. TestKit verifies that an unchanged task is up to date, class changes trigger generation, clean resource builds have no task cycle, and the task works with Gradle's configuration cache. The plugin was developed and tested with the repository's Gradle Wrapper version, 9.8.1; older Gradle versions are not currently covered by compatibility tests.

## YAML merging limitations

The source descriptor is safely loaded and validated as a YAML mapping. Existing root metadata, commands, and non-conflicting manually declared permission entries are retained. If a manually declared permission node overlaps with a generated permission or group node, generation fails instead of silently replacing either definition.

SnakeYAML serializes the merged document, so comments, whitespace, quoting style, and original formatting can be normalized. YAML values and unrelated entries are preserved as parsed data. Duplicate YAML mapping keys are rejected.

The result represents catalog group grants as flattened positive children. Bukkit defaults, permission attachments, other native registrations, and external permission managers can add or override effective assignments. Catalog exclusions are not global denials, and the generated file does not guarantee identical authorization results under every combination of server permission sources.
