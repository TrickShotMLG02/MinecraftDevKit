# MinecraftDevKit

MinecraftDevKit is a modular, reusable Java development toolkit for Minecraft server plugins and mods. It is being built around platform-independent libraries with small, platform-specific adapters.

> **Status:** Typed permission definitions and metadata generation, Cloud-backed command definitions and dispatch, reloadable YAML localization, typed YAML configuration with atomic snapshots and migrations, managed Bukkit/Paper inventory GUIs, task scheduling, and typed lifecycle/service management are implemented. Bukkit/Paper examples are build-verified; live server testing remains a manual step.

## Goals

- Reduce repeated boilerplate across projects.
- Promote reusable, maintainable implementations.
- Provide platform-independent APIs where possible.
- Support multiple Minecraft platforms through adapters.
- Let developers include only the modules they need.
- Centralize fixes and improvements.

## Architecture

Shared modules contain ordinary Java APIs and must not depend on Bukkit, Paper, Velocity, Fabric, Forge, NeoForge, or Minecraft internals. Platform adapter modules may depend on shared modules and the relevant platform API when actual integrations are added. This keeps shared code usable in different environments and lets consumers choose the artifacts they need.

## Modules

| Module | Responsibility | Current state |
| --- | --- | --- |
| `devkit-core` | Platform-independent interfaces and foundational abstractions | Build and package structure only |
| `devkit-text` | Immutable text references, resolution contracts, and reference catalogs | Implemented; Java 17 |
| `devkit-localization` | Locale resolution, layered immutable snapshots, reload coordination, diagnostics, and shared scanner logic | Implemented; Java 17 |
| `devkit-localization-yaml` | Safe YAML bundled/external translation sources | Implemented; Java 17 |
| `devkit-localization-cli` | Terminal scanner for `.properties`/YAML resources and reference manifests | Implemented; Java 17 |
| `devkit-config` | Platform-independent typed keys, codecs, schemas, validation, snapshots, updates, and listeners | Implemented; Java 17 |
| `devkit-config-yaml` | Safe YAML loading, defaults, unknown-field preservation, atomic save/reload, watching, and migrations | Implemented; Java 17 |
| `devkit-scheduler` | Platform-independent tick schedules, task handles, cancellation, nested scopes, and async result handoff contracts | Implemented; Java 17 |
| `devkit-lifecycle` | Platform-independent typed service keys, dependency ordering, lifecycle state, rollback, resource ownership, diagnostics, and reload hooks | Implemented; Java 17 |
| `devkit-permissions` | Platform-independent permission definitions, sections, groups, and immutable catalog resolution | Implemented; depends on `devkit-core` |
| `devkit-commands` | Immutable typed command definitions, Cloud v2 dispatch, suggestions, generated help/usage, and localized errors | Implemented; Java 17 |
| `devkit-bukkit` | Common Bukkit/Spigot/Paper adapters | Minimal package structure; depends on shared modules |
| `devkit-bukkit-commands` | Cloud v2 command registration, Bukkit sender bridge, player arguments, localized messages | Implemented for JavaPlugin lifecycle; Java 21 |
| `devkit-bukkit-gui` | Managed Bukkit/Paper chest menus, isolated player sessions, safe interaction handling, pagination, controls, skull profiles, and reactive refresh | Implemented against Paper 1.21.11; Java 21 |
| `devkit-bukkit-scheduler` | Plugin-owned native Bukkit task scheduling, sync/async dispatch, failure reporting, and disable lifecycle cleanup | Implemented against Paper 1.21.11; Java 21; not Folia compatible |
| `devkit-bukkit-lifecycle` | Synchronous JavaPlugin lifecycle bridge with thread checks, plugin logging, and disable cleanup | Implemented against Paper 1.21.11; Java 21; not Folia compatible |
| `devkit-bukkit-metadata` | Bukkit-compatible build-time metadata exporters, including permission YAML | Permission catalog exporter implemented; Java 17; does not require a server or Bukkit API |
| `devkit-gradle-plugin` | Gradle plugins for Bukkit permission metadata and localization scanning | Implemented; Java 17; Maven Local and JitPack module publication configured; public JitPack build not verified |

See the [`devkit-permissions` guide](devkit-permissions/README.md), [`devkit-commands` guide](devkit-commands/README.md), [`devkit-text` guide](devkit-text/README.md), [`devkit-localization` guide](devkit-localization/README.md), [`devkit-localization-yaml` guide](devkit-localization-yaml/README.md), [`devkit-config` guide](devkit-config/README.md), [`devkit-config-yaml` guide](devkit-config-yaml/README.md), [`devkit-scheduler` guide](devkit-scheduler/README.md), [`devkit-lifecycle` guide](devkit-lifecycle/README.md), [`devkit-bukkit-commands` guide](devkit-bukkit-commands/README.md), [`devkit-bukkit-gui` guide](devkit-bukkit-gui/README.md), [`devkit-bukkit-scheduler` guide](devkit-bukkit-scheduler/README.md), [`devkit-bukkit-lifecycle` guide](devkit-bukkit-lifecycle/README.md), and [`devkit-gradle-plugin` guide](devkit-gradle-plugin/README.md). Configuration comments and formatting are normalized only when explicit full-document save is invoked; unknown fields are retained. Shared configuration, command, text, localization, scheduler, and lifecycle modules contain no Bukkit API dependency.

The standalone [`Bukkit permissions example`](examples/bukkit-permissions-example/README.md) supports explicit Maven Local and JitPack dependency modes, generates permission metadata during its own Gradle build, and packages a test plugin JAR for a Paper server. The standalone [`Bukkit commands example`](examples/bukkit-commands-example/README.md) supports the same repository modes, generates permission metadata and a localization reference manifest, validates English/German YAML catalogs with partial regional overrides, and packages a Cloud-backed plugin with reloadable external translations. The repository also includes a local [Paper test server provisioner and IntelliJ run/debug profiles](tools/devservers/README.md); server download and startup are opt-in and Paper's EULA is never accepted automatically.

The independent [`Bukkit configuration example`](examples/bukkit-config-example/README.md) demonstrates typed settings, schema validation, programmatic updates, safe persistence, reload conflicts, and schema migration using the existing Cloud command adapter.

The standalone [`Bukkit GUI example`](examples/bukkit-gui-example/README.md) demonstrates localized chest menus, protected inventory interaction, per-player navigation/state, online player pagination, controls, async skull profiles, and Paper event-driven refresh using the published GUI module from Maven Local.

The standalone [`Bukkit scheduler example`](examples/bukkit-scheduler-example/README.md) demonstrates plugin-owned immediate, delayed, repeating, scoped, and asynchronous tasks, with a nonblocking handoff back to the Paper server thread.

The standalone [`Bukkit lifecycle example`](examples/bukkit-lifecycle-example/README.md) demonstrates typed service keys, dependency-ordered startup, managed task scopes and resources, reload hooks, diagnostics, and reverse-order shutdown.

## Installation

After the repository is public, tag `0.1.0-beta.1`, and JitPack successfully builds that tag, consumers can declare individual modules using JitPack coordinates. These examples document the intended prerelease coordinates; no public artifact or JitPack build has been verified yet.

### Gradle Kotlin DSL

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.TrickShotMLG02.MinecraftDevKit:devkit-permissions:0.1.0-beta.1")
}
```

### Maven

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.github.TrickShotMLG02.MinecraftDevKit</groupId>
    <artifactId>devkit-permissions</artifactId>
    <version>0.1.0-beta.1</version>
  </dependency>
</dependencies>
```

Declaring a dependency makes it available to compile and run your project. It does not by itself package the dependency inside a Minecraft plugin or mod JAR. Follow the packaging and dependency-distribution requirements of the target platform; this project does not currently provide a shading or bundling plugin.

For local development, each module also publishes under `com.trickshotmlg.devkit` and the configured project version. Run `publishToMavenLocal` to install those artifacts into your local Maven repository.

### Applying the Gradle plugin from JitPack

Gradle plugin marker artifacts use the plugin ID as their group. JitPack's documented multi-module coordinates instead use `com.github.TrickShotMLG02.MinecraftDevKit` as the group and `devkit-gradle-plugin` as the artifact. Resolve the plugin request directly to that published module:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.trickshotmlg.devkit.bukkit-permissions"
                    || requested.id.id == "com.trickshotmlg.devkit.localization-scan") {
                useModule("com.github.TrickShotMLG02.MinecraftDevKit:devkit-gradle-plugin:${requested.version}")
            }
        }
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    java
    id("com.trickshotmlg.devkit.bukkit-permissions") version "0.1.0-beta.1"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.TrickShotMLG02.MinecraftDevKit:devkit-permissions:0.1.0-beta.1")
}

minecraftDevKit {
    bukkitPermissions {
        catalogClass.set("com.example.permissions.PluginPermissions")
    }
}
```

JitPack builds multi-module artifacts using `com.github.USER.REPO:MODULE:TAG`. The plugin's generated marker publication is also available from local Maven publication, but consumers should use the module mapping above for JitPack instead of assuming the marker group is hosted there.

## Building

The project uses JDK 21 to run Gradle and build the Bukkit adapters, and JDK 17 to compile shared libraries, metadata exporter, and Gradle plugin. Ensure both toolchains are available to Gradle. Shared libraries including `devkit-scheduler`, `devkit-lifecycle`, `devkit-bukkit-metadata`, and `devkit-gradle-plugin` compile to Java 17 bytecode; Bukkit runtime adapters including `devkit-bukkit-scheduler` and `devkit-bukkit-lifecycle` target Java 21.

From the repository root, run the appropriate wrapper command:

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat publishToMavenLocal
.\gradlew.bat clean
```

```sh
./gradlew build
./gradlew test
./gradlew publishToMavenLocal
./gradlew clean
```

## Platform compatibility

The common modules are normal Java libraries and do not require a Minecraft runtime. `devkit-bukkit-metadata` and `devkit-gradle-plugin` generate Bukkit `plugin.yml` permissions without starting a server. `devkit-bukkit-commands` provides Cloud-backed runtime command registration for Bukkit/Spigot-style JavaPlugin lifecycle and Paper's legacy manager with native Brigadier enabled when available. `devkit-bukkit-gui`, `devkit-bukkit-scheduler`, and `devkit-bukkit-lifecycle` are compiled against Paper 1.21.11 and use Bukkit's JavaPlugin lifecycle and traditional synchronous server thread; older server versions, other platforms, Folia scheduling, and live-server behavior have not been verified. Modern Paper bootstrap command registration and other platform adapters remain future work. Java compatibility depends on the selected module: shared modules, metadata export, and the Gradle plugin target Java 17, while Bukkit adapters target Java 21.

## Roadmap

- Platform-specific runtime permission integrations.
- Paper bootstrap-time command registration and Velocity/mod-loader adapters.
- Additional messaging and localization integrations.
- General utilities.
- Additional Bukkit/Paper, Velocity, Fabric, Forge, and NeoForge integrations.

Permission declarations, catalog resolution, Bukkit-compatible permission metadata export, command definitions and Cloud dispatch, generated help/usage, YAML localization with locale fallback and atomic reload, typed YAML configuration with safe persistence and migrations, localization diagnostics, automatic Gradle resource integration, the JavaPlugin command adapter, and the Bukkit task scheduler are implemented. Per-player language preferences, Paper bootstrap registration, Folia scheduling, and the remaining platform adapters are planned work.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup, design guidelines, compatibility expectations, and pull-request guidance.

See [RELEASING.md](RELEASING.md) for the `0.1.0-beta.1` prerelease readiness checklist. No tag or release has been created.

## License

MinecraftDevKit is licensed under the GNU General Public License v3.0 only (`GPL-3.0-only`). See [LICENSE](LICENSE) for the complete license text.
