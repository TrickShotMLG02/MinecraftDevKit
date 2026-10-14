# Contributing

Thanks for considering a contribution to MinecraftDevKit.

## Project setup

Install JDK 17 and JDK 21, then open the repository root in IntelliJ IDEA as an existing Gradle project. Use JDK 21 as the Gradle JVM. You can also use `gradlew.bat` on Windows or `./gradlew` on Linux/macOS. The build uses Java toolchains: common modules target Java 17 and the Bukkit adapter targets Java 21.

## Code organization

- Put platform-independent APIs and implementations in the shared modules.
- Put integrations with a server, loader, or game API in a dedicated adapter module.
- Keep dependencies directional: adapters may depend on shared modules; shared modules must not depend on adapters.
- Add new platform modules only when there is real platform-specific code to house.
- Keep APIs small and avoid introducing abstractions before there is a concrete use case.

## Module design

Use `api` for dependencies whose types form part of a module's public API, `implementation` for internal runtime dependencies, and `compileOnly` for APIs supplied by the host platform. Do not package Minecraft server APIs into library artifacts. Add repositories only when a dependency cannot be resolved from Maven Central.

## Build and tests

Before opening a pull request, run `./gradlew build` (or `gradlew.bat build` on Windows). Add focused JUnit 5 tests for behavior introduced by a change. Keep shared modules compatible with Java 17 and do not raise a module's baseline without a clear need.

## Pull requests

- Keep changes focused and explain their purpose and user impact.
- Include tests and update documentation when behavior or setup changes.
- Avoid unrelated formatting or generated files.
- Do not add credentials, machine-specific paths, or published artifacts.
- Confirm that licensing and third-party dependency terms are compatible with GPL-3.0-only.
