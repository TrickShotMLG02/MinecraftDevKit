# MinecraftDevKit

MinecraftDevKit is a modular, reusable Java development toolkit for Minecraft server plugins and mods. It is being built around platform-independent libraries with small, platform-specific adapters.

> **Status:** Typed permission definitions and metadata generation, Cloud-backed command definitions and dispatch, reloadable YAML localization, typed YAML configuration with atomic snapshots and migrations, managed Bukkit/Paper inventory GUIs, task scheduling, and typed lifecycle/service management are implemented. Spigot 1.21.11, Paper Bukkit-plugin, and native Paper plugin paths have automated real-server verification; authenticated-player interaction checks remain a separate manual step.

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
| `devkit-storage` | Explicit-SQL JDBC storage with HikariCP, SQLite defaults, prepared parameters, typed row mapping, transactions, and bounded async work | Implemented; Java 17 |
| `devkit-storage-migrations` | Optional Flyway versioned SQL migration integration for SQLite/MySQL/MariaDB | Implemented; Java 17 |
| `devkit-permissions` | Platform-independent permission definitions, sections, groups, and immutable catalog resolution | Implemented; depends on `devkit-core` |
| `devkit-commands` | Immutable typed command definitions, Cloud v2 dispatch, suggestions, generated help/usage, and localized errors | Implemented; Java 17 |
| `devkit-bukkit` | Common Bukkit/Spigot/Paper adapters | Minimal package structure; depends on shared modules |
| `devkit-bukkit-commands` | Cloud v2 command registration, Bukkit sender bridge, player arguments, localized messages | Compiled against Spigot API 1.21.11; Cloud legacy Bukkit path; Java 21 |
| `devkit-bukkit-permissions` | Registers resolved permission catalog definitions and groups with Bukkit's runtime permission manager | Spigot/Paper API 1.21.11 compile target; owned cleanup and metadata conflict validation; Java 21 |
| `devkit-bukkit-gui` | Managed chest menus, isolated sessions, safe interactions, pagination, controls, skull profiles, reactive refresh | Compiled against Spigot API 1.21.11; Java 21; Paper enhancements optional |
| `devkit-paper-commands` | Incendo Cloud native Paper command lifecycle and Brigadier registration for shared command trees | Optional Paper API 1.21.11 extension; Java 21 |
| `devkit-paper-gui` | Native Adventure GUI metadata and component inventory titles | Optional Paper 1.21.11 extension; Java 21 |
| `devkit-paper-lifecycle` | Bootstrap-safe Paper datapack-discovery lifecycle hook; shared runtime services remain on BukkitLifecycle | Optional Paper API 1.21.11 extension; Java 21 |
| `devkit-bukkit-scheduler` | Plugin-owned native Bukkit task scheduling, sync/async dispatch, failure reporting, disable cleanup | Compiled against Spigot API 1.21.11; Java 21; not Folia compatible |
| `devkit-bukkit-lifecycle` | Synchronous JavaPlugin lifecycle bridge with thread checks, plugin logging, disable cleanup | Compiled against Spigot API 1.21.11; Java 21; not Folia compatible |
| `devkit-bukkit-metadata` | Bukkit-compatible build-time metadata exporters, including permission YAML | Permission catalog exporter implemented; Java 17; does not require a server or Bukkit API |
| `devkit-gradle-plugin` | Gradle plugins for Bukkit permission metadata and localization scanning | Implemented; Java 17; Maven Local and JitPack module publication configured; public JitPack build not verified |

See the [`devkit-permissions` guide](devkit-permissions/README.md), [`devkit-bukkit-permissions` guide](devkit-bukkit-permissions/README.md), [`devkit-commands` guide](devkit-commands/README.md), [`devkit-text` guide](devkit-text/README.md), [`devkit-localization` guide](devkit-localization/README.md), [`devkit-localization-yaml` guide](devkit-localization-yaml/README.md), [`devkit-config` guide](devkit-config/README.md), [`devkit-config-yaml` guide](devkit-config-yaml/README.md), [`devkit-scheduler` guide](devkit-scheduler/README.md), [`devkit-lifecycle` guide](devkit-lifecycle/README.md), [`devkit-bukkit-commands` guide](devkit-bukkit-commands/README.md), [`devkit-paper-commands` guide](devkit-paper-commands/README.md), [`devkit-paper-lifecycle` guide](devkit-paper-lifecycle/README.md), [`devkit-bukkit-gui` guide](devkit-bukkit-gui/README.md), [`devkit-paper-gui` guide](devkit-paper-gui/README.md), [`devkit-bukkit-scheduler` guide](devkit-bukkit-scheduler/README.md), [`devkit-bukkit-lifecycle` guide](devkit-bukkit-lifecycle/README.md), and [`devkit-gradle-plugin` guide](devkit-gradle-plugin/README.md). Configuration comments and formatting are normalized only when explicit full-document save is invoked; unknown fields are retained. Shared configuration, command, text, localization, scheduler, and lifecycle modules contain no Bukkit API dependency.

The JDBC storage modules are documented in the [`devkit-storage` guide](devkit-storage/README.md) and optional [`devkit-storage-migrations` guide](devkit-storage-migrations/README.md). SQLite JDBC is included by the storage module; applications add the MySQL or MariaDB JDBC driver they intend to use. Storage callbacks are asynchronous only when submitted through `Storage.async()` and must use an explicit scheduler handoff before touching Bukkit state.

The standalone [`Bukkit permissions example`](examples/bukkit-permissions-example/README.md) supports explicit Maven Local and JitPack dependency modes, generates permission metadata during its own Gradle build, and packages a Spigot-compatible plugin JAR. The standalone [`Bukkit commands example`](examples/bukkit-commands-example/README.md) supports the same repository modes, generates permission metadata and a localization reference manifest, validates English/German YAML catalogs with partial regional overrides, and packages a Cloud-backed plugin with reloadable external translations. The combined integration example explicitly selects the legacy Bukkit command backend for Spigot and the native Cloud Paper backend for Paper while sharing its command definitions. The repository also includes a local [Paper test server provisioner and IntelliJ run/debug profiles](tools/devservers/README.md); server download and startup are opt-in and Paper's EULA is never accepted automatically.

The independent [`Bukkit configuration example`](examples/bukkit-config-example/README.md) demonstrates typed settings, schema validation, programmatic updates, safe persistence, reload conflicts, and schema migration using the existing Cloud command adapter.

The standalone [`Bukkit GUI example`](examples/bukkit-gui-example/README.md) demonstrates localized chest menus, protected inventory interaction, per-player navigation/state, online player pagination, controls, async skull profiles, and Paper-native component/profile behavior using the published GUI modules from Maven Local.

The standalone [`Bukkit scheduler example`](examples/bukkit-scheduler-example/README.md) demonstrates plugin-owned immediate, delayed, repeating, scoped, and asynchronous tasks, with a nonblocking handoff back to the Paper server thread.

The standalone [`Bukkit lifecycle example`](examples/bukkit-lifecycle-example/README.md) demonstrates typed service keys, dependency-ordered startup, managed task scopes and resources, reload hooks, diagnostics, and reverse-order shutdown.

The standalone [`Bukkit integration example`](examples/bukkit-integration-example/README.md) composes lifecycle, typed YAML configuration, English/German localization, generated typed permissions, Cloud commands, protected paginated GUIs, and lifecycle-owned scheduled/asynchronous work. The same sources have Paper and Spigot build modes.

The standalone [`Bukkit storage example`](examples/bukkit-storage-example/README.md) demonstrates lifecycle-owned SQLite persistence, versioned migrations, a typed player repository, async balance reads, atomic updates, and safe handoff to the Paper thread.

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

The project uses JDK 21 to run Gradle and build Bukkit/Paper adapters, and JDK 17 to compile shared libraries, metadata exporter, and Gradle plugin. Ensure both toolchains are available to Gradle. Shared libraries including `devkit-scheduler`, `devkit-lifecycle`, `devkit-bukkit-metadata`, and `devkit-gradle-plugin` compile to Java 17 bytecode; Bukkit runtime adapters, including `devkit-bukkit-permissions`, `devkit-paper-commands`, `devkit-bukkit-scheduler`, `devkit-bukkit-lifecycle`, and optional `devkit-paper-gui`, target Java 21.

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

The common modules are ordinary Java libraries and do not require a Minecraft runtime. `devkit-bukkit-metadata` and `devkit-gradle-plugin` generate Bukkit `plugin.yml` permissions without starting a server; the Gradle plugin can also opt in to a native `paper-plugin.yml`. The common Bukkit command, GUI, scheduler, lifecycle, and runtime permission adapters compile against Spigot API `1.21.11-R0.1-SNAPSHOT`; `devkit-paper-commands`, `devkit-paper-gui`, and `devkit-paper-lifecycle` are optional Paper 1.21.11 extensions. Native Paper bootstrap is limited to a bootstrap-safe datapack-discovery callback; normal services and Cloud commands initialize from the JavaPlugin runtime phase. The combined example selects legacy Cloud registration on Spigot and native `PaperCommandManager` on Paper, with the same shared definitions. Runtime permissions validate/reuse matching Bukkit metadata and register only missing definitions; catalog exclusions omit positive child grants and never become negative assignments. Bukkit adapters use the traditional global server thread and are not Folia-compatible. Paper bootstrap command registration, older server versions, Folia, and non-Bukkit platforms remain unsupported or unverified.

## Roadmap

- Platform-specific runtime permission integrations.
- Cloud bootstrap-time command registration and Velocity/mod-loader adapters.
- Additional messaging and localization integrations.
- General utilities.
- Additional Bukkit/Paper, Velocity, Fabric, Forge, and NeoForge integrations.

Permission declarations, catalog resolution, Bukkit-compatible permission metadata export, opt-in native Paper plugin metadata generation, command definitions and Cloud dispatch, generated help/usage, YAML localization with locale fallback and atomic reload, typed YAML configuration with safe persistence and migrations, localization diagnostics, automatic Gradle resource integration, the JavaPlugin command adapter, bootstrap-safe Paper datapack discovery, and the Bukkit task scheduler are implemented. Per-player language preferences, Cloud bootstrap command registration, Folia scheduling, and the remaining platform adapters are planned work.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup, design guidelines, compatibility expectations, and pull-request guidance.

See [RELEASING.md](RELEASING.md) for the `0.1.0-beta.1` prerelease readiness checklist. No tag or release has been created.

## License

MinecraftDevKit is licensed under the GNU General Public License v3.0 only (`GPL-3.0-only`). See [LICENSE](LICENSE) for the complete license text.
