# MinecraftDevKit

MinecraftDevKit is a modular, reusable Java development toolkit for Minecraft server plugins and mods. It is being built around platform-independent libraries with small, platform-specific adapters.

> **Status:** Permission definitions, Bukkit `plugin.yml` permission export, and its Gradle build integration are implemented. Command, configuration, and runtime platform integrations remain planned.

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
| `devkit-permissions` | Platform-independent permission definitions, sections, groups, and immutable catalog resolution | Implemented; depends on `devkit-core` |
| `devkit-commands` | Platform-independent command definitions and execution contracts | Build structure only; depends on core and permissions |
| `devkit-bukkit` | Common Bukkit/Spigot/Paper adapters | Minimal package structure; depends on shared modules |
| `devkit-bukkit-metadata` | Bukkit-compatible build-time metadata exporters, including permission YAML | Permission catalog exporter implemented; Java 17; does not require a server or Bukkit API |
| `devkit-gradle-plugin` | Gradle plugin that merges catalog permissions into traditional Bukkit/Spigot `plugin.yml` | Implemented; Java 17; local Maven publication only |

See the [`devkit-permissions` guide](devkit-permissions/README.md), [`devkit-bukkit-metadata` guide](devkit-bukkit-metadata/README.md), and [`devkit-gradle-plugin` guide](devkit-gradle-plugin/README.md). The Gradle plugin supports traditional Bukkit/Spigot-style `plugin.yml`; Paper's `paper-plugin.yml`, Velocity, Fabric, Forge, and NeoForge metadata are not supported. No platform API dependency is included in the permissions module.

The standalone [`Bukkit permissions example`](examples/bukkit-permissions-example/README.md) consumes the Maven Local publications, generates permission metadata during its own Gradle build, and packages a test plugin JAR for a Paper server.

## Installation

After the repository has been pushed, a version has been tagged, and JitPack has successfully built it, add JitPack to the consuming project's repositories and declare the module dependency. These examples describe future consumption; no public artifact is available yet.

### Gradle Kotlin DSL

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.TrickshotMLG.MinecraftDevKit:devkit-permissions:0.1.0")
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
    <version>0.1.0</version>
  </dependency>
</dependencies>
```

Declaring a dependency makes it available to compile and run your project. It does not by itself package the dependency inside a Minecraft plugin or mod JAR. Follow the packaging and dependency-distribution requirements of the target platform; this project does not currently provide a shading or bundling plugin.

For local development, each module also publishes under `com.trickshotmlg.devkit` and the configured project version. Run `publishToMavenLocal` to install those artifacts into your local Maven repository.

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

The common modules are normal Java libraries and do not currently require a Minecraft runtime. `devkit-bukkit-metadata` and `devkit-gradle-plugin` generate Bukkit `plugin.yml` permissions without starting a server. Runtime integration with Bukkit, Paper, Velocity, Fabric, Forge, and NeoForge requires platform-specific adapters. Java compatibility depends on the selected module: shared modules, metadata export, and the Gradle plugin target Java 17, while `devkit-bukkit` targets Java 21.

## Roadmap

- Platform-specific runtime permission integrations.
- A platform-independent command framework.
- Configuration management.
- Messaging and localization.
- Scheduling abstractions.
- General utilities.
- Bukkit/Paper, Velocity, Fabric, Forge, and NeoForge integrations.

Permission declarations, catalog resolution, Bukkit-compatible permission metadata export, and automatic Gradle resource integration are implemented. Runtime registration and the remaining roadmap items are planned work.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup, design guidelines, compatibility expectations, and pull-request guidance.

## License

MinecraftDevKit is licensed under the GNU General Public License v3.0 only (`GPL-3.0-only`). See [LICENSE](LICENSE) for the complete license text.
