# MinecraftDevKit

MinecraftDevKit is a modular, reusable Java development toolkit for Minecraft server plugins and mods. It is being built around platform-independent libraries with small, platform-specific adapters.

> **Status:** Permission definitions and metadata generation, Cloud-backed command definitions, text/localization providers, and localization diagnostics are implemented. Bukkit/Paper runtime command registration remains planned.

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
| `devkit-localization` | UTF-8 locale catalogs, fallback resolution, diagnostics, and localization scanning | Implemented; Java 17 |
| `devkit-localization-cli` | Terminal scanner for translation resources and reference manifests | Implemented; Java 17 |
| `devkit-permissions` | Platform-independent permission definitions, sections, groups, and immutable catalog resolution | Implemented; depends on `devkit-core` |
| `devkit-commands` | Immutable typed command definitions, Cloud v2 dispatch, suggestions, generated help/usage, and localized errors | Implemented; Java 17 |
| `devkit-bukkit` | Common Bukkit/Spigot/Paper adapters | Minimal package structure; depends on shared modules |
| `devkit-bukkit-commands` | Cloud v2 command registration, Bukkit sender bridge, player arguments, localized messages | Implemented for JavaPlugin lifecycle; Java 21 |
| `devkit-bukkit-metadata` | Bukkit-compatible build-time metadata exporters, including permission YAML | Permission catalog exporter implemented; Java 17; does not require a server or Bukkit API |
| `devkit-gradle-plugin` | Gradle plugins for Bukkit permission metadata and localization scanning | Implemented; Java 17; Maven Local and JitPack module publication configured; public JitPack build not verified |

See the [`devkit-permissions` guide](devkit-permissions/README.md), [`devkit-commands` guide](devkit-commands/README.md), [`devkit-text` guide](devkit-text/README.md), [`devkit-localization` guide](devkit-localization/README.md), [`devkit-bukkit-commands` guide](devkit-bukkit-commands/README.md), and [`devkit-gradle-plugin` guide](devkit-gradle-plugin/README.md). The metadata plugin supports traditional Bukkit/Spigot-style `plugin.yml`; Paper's `paper-plugin.yml`, Velocity, Fabric, Forge, and NeoForge metadata are not supported. Shared command, text, and localization modules contain no Bukkit API dependency.

The standalone [`Bukkit permissions example`](examples/bukkit-permissions-example/README.md) resolves MinecraftDevKit from JitPack and the Paper API from PaperMC, generates permission metadata during its own Gradle build, and packages a test plugin JAR for a Paper server. The repository also includes a local [Paper test server provisioner and IntelliJ run/debug profiles](tools/devservers/README.md); server download and startup are opt-in and Paper's EULA is never accepted automatically.

## Installation

After the repository is public, tag `0.1.0-beta.1`, and JitPack successfully builds that tag, consumers can declare individual modules using JitPack coordinates. These examples document the intended prerelease coordinates; no public artifact or JitPack build has been verified yet.

### Gradle Kotlin DSL

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.TrickshotMLG.MinecraftDevKit:devkit-permissions:0.1.0-beta.1")
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
    <groupId>com.github.TrickshotMLG.MinecraftDevKit</groupId>
    <artifactId>devkit-permissions</artifactId>
    <version>0.1.0-beta.1</version>
  </dependency>
</dependencies>
```

Declaring a dependency makes it available to compile and run your project. It does not by itself package the dependency inside a Minecraft plugin or mod JAR. Follow the packaging and dependency-distribution requirements of the target platform; this project does not currently provide a shading or bundling plugin.

For local development, each module also publishes under `com.trickshotmlg.devkit` and the configured project version. Run `publishToMavenLocal` to install those artifacts into your local Maven repository.

### Applying the Gradle plugin from JitPack

Gradle plugin marker artifacts use the plugin ID as their group. JitPack's documented multi-module coordinates instead use `com.github.TrickshotMLG.MinecraftDevKit` as the group and `devkit-gradle-plugin` as the artifact. Resolve the plugin request directly to that published module:

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
                useModule("com.github.TrickshotMLG.MinecraftDevKit:devkit-gradle-plugin:${requested.version}")
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
    implementation("com.github.TrickshotMLG.MinecraftDevKit:devkit-permissions:0.1.0-beta.1")
}

minecraftDevKit {
    bukkitPermissions {
        catalogClass.set("com.example.permissions.PluginPermissions")
    }
}
```

JitPack builds multi-module artifacts using `com.github.USER.REPO:MODULE:TAG`. The plugin's generated marker publication is also available from local Maven publication, but consumers should use the module mapping above for JitPack instead of assuming the marker group is hosted there.

## Building

The project uses JDK 21 to run Gradle and build the Bukkit adapter, and JDK 17 to compile shared libraries, metadata exporter, and Gradle plugin. Ensure both toolchains are available to Gradle. Shared libraries, `devkit-bukkit-metadata`, and `devkit-gradle-plugin` compile to Java 17 bytecode; `devkit-bukkit` targets Java 21.

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

The common modules are normal Java libraries and do not require a Minecraft runtime. `devkit-bukkit-metadata` and `devkit-gradle-plugin` generate Bukkit `plugin.yml` permissions without starting a server. `devkit-bukkit-commands` provides runtime command registration for Bukkit/Spigot-style JavaPlugin lifecycle and Paper's legacy manager with native Brigadier enabled when available. Modern Paper bootstrap registration and other platform adapters remain future work. Java compatibility depends on the selected module: shared modules, metadata export, and the Gradle plugin target Java 17, while Bukkit adapters target Java 21.

## Roadmap

- Platform-specific runtime permission integrations.
- Paper bootstrap-time command registration and Velocity/mod-loader adapters.
- Configuration management.
- Messaging and localization.
- Scheduling abstractions.
- General utilities.
- Additional Bukkit/Paper, Velocity, Fabric, Forge, and NeoForge integrations.

Permission declarations, catalog resolution, Bukkit-compatible permission metadata export, and automatic Gradle resource integration are implemented. Runtime registration and the remaining roadmap items are planned work.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup, design guidelines, compatibility expectations, and pull-request guidance.

See [RELEASING.md](RELEASING.md) for the `0.1.0-beta.1` prerelease readiness checklist. No tag or release has been created.

## License

MinecraftDevKit is licensed under the GNU General Public License v3.0 only (`GPL-3.0-only`). See [LICENSE](LICENSE) for the complete license text.
