# MinecraftDevKit Bukkit Permissions Gradle Plugin

The `devkit-gradle-plugin` module provides `com.trickshotmlg.devkit.bukkit-permissions` and `com.trickshotmlg.devkit.localization-scan`. The first loads a compiled Java `PermissionCatalog`, exports traditional Bukkit/Spigot `plugin.yml` permission metadata, and merges that metadata during normal Gradle resource processing. The second validates UTF-8 localization catalogs against a JSON text-reference manifest.

This plugin does not generate Paper `paper-plugin.yml`, command metadata, runtime registrations, or metadata for Velocity, Fabric, Forge, or NeoForge. It has no server runtime requirement.

## Localization scanning

The scanner API and standalone CLI live in `devkit-localization` and `devkit-localization-cli`; they do not depend on Bukkit. The Gradle plugin adds a task to the consuming project:

```kotlin
plugins {
    id("com.trickshotmlg.devkit.localization-scan") version "0.1.0-SNAPSHOT"
}

devkitLocalizationScan {
    translationsDirectory.set(layout.projectDirectory.dir("src/main/resources/translations"))
    referenceManifest.set(layout.projectDirectory.file("src/main/localization/text-references.json"))
    baseLocale.set("en-US")
    requiredLocales.set(listOf("en-US", "de-DE"))
    failOn.set(listOf("MISSING_FALLBACK", "PLACEHOLDER_MISMATCH", "INVALID_RESOURCE"))
}
```

Run `scanDevKitTranslations` directly or attach it to `check`. It writes a deterministic JSON report to `build/reports/minecraftdevkit/localization.json`. Defaults expect `src/main/resources/translations`, `src/main/localization/text-references.json`, and base locale `en-US`. The task declares its directory and manifest as inputs and the JSON report as its output; it supports Gradle configuration cache.

Reference manifests can be generated from `TextCatalogManifest.write(...)`, including command descriptions, usage overrides, argument descriptions, framework error messages, and application-owned references. Dynamic translation keys must be registered explicitly; arbitrary Java source is not scanned with regexes. A fallback changes what text is displayed, but does not suppress a missing-translation finding.

## Publishing and applying

The plugin implementation is published as the `devkit-gradle-plugin` module. For JitPack, the module coordinates are `com.github.TrickshotMLG.MinecraftDevKit:devkit-gradle-plugin:<tag>`.

The standard Gradle plugin marker has group `com.trickshotmlg.devkit.bukkit-permissions`, derived from the plugin ID. JitPack documents repository modules under `com.github.USER.REPO:MODULE:TAG`, so do not rely on the marker coordinate being available from JitPack. Use `pluginManagement.resolutionStrategy` to map the plugin request to its implementation module:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.trickshotmlg.devkit.bukkit-permissions") {
                useModule("com.github.TrickshotMLG.MinecraftDevKit:devkit-gradle-plugin:${requested.version}")
            }
        }
    }
}
```

Then apply the plugin in a project build:

```kotlin
plugins {
    java
    id("com.trickshotmlg.devkit.bukkit-permissions") version "0.1.0-beta.1"
}
```

Add JitPack to normal dependency resolution as well when the consuming project depends on MinecraftDevKit modules.

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
