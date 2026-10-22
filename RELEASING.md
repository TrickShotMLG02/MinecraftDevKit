# Prerelease checklist

This checklist prepares `0.1.0-beta.1`. It does not create a tag, GitHub release, or remote publication.

## Before tagging

- [ ] Confirm the public GitHub repository is `TrickShotMLG02/MinecraftDevKit` and that the intended commit is pushed to the default branch.
- [ ] Review `CHANGELOG.md` and describe the implemented permission catalog, Bukkit metadata exporter, Gradle generation plugin, and known limitations.
- [ ] Confirm `LICENSE` is the complete GNU GPL version 3 text and all module POMs identify `GPL-3.0-only`.
- [ ] Confirm the version string to publish is exactly `0.1.0-beta.1`; use that exact tag, without a `v` prefix, because it becomes the JitPack artifact version.
- [ ] Ensure JDK 21 is installed and Gradle can discover both Java 17 and Java 21 toolchains.
- [ ] Run the root checks from a clean checkout:

  ```powershell
  .\gradlew.bat clean build publishToMavenLocal
  ```

  ```sh
  ./gradlew clean build publishToMavenLocal
  ```

- [ ] Inspect the six module publications (`devkit-core`, `devkit-permissions`, `devkit-commands`, `devkit-bukkit`, `devkit-bukkit-metadata`, `devkit-gradle-plugin`) for matching version, project URL, SCM, license, JAR, sources JAR, Javadoc JAR, and correct transitive dependencies.
- [ ] Confirm plugin marker metadata points to `com.github.TrickShotMLG02.MinecraftDevKit:devkit-gradle-plugin:0.1.0-beta.1` in the JitPack-style build. Consumers should still use the documented `pluginManagement.resolutionStrategy` module mapping because marker hosting under its plugin-ID group is not guaranteed by JitPack's multi-module convention.
- [ ] Build the standalone example against the published JitPack version and inspect its JAR for one merged `plugin.yml`, bundled MinecraftDevKit runtime classes, and no Bukkit/Paper API classes.

## Tag and JitPack validation

- [ ] Create and push the Git tag `0.1.0-beta.1` only after the pre-tag checks pass.
- [ ] Request the JitPack build for `TrickShotMLG02/MinecraftDevKit` at `0.1.0-beta.1` and wait for a successful result.
- [ ] Confirm JitPack exposes the individual module coordinates under `com.github.TrickShotMLG02.MinecraftDevKit`, including the Gradle plugin implementation module and its runtime dependencies.
- [ ] Test a clean external Gradle consumer for `devkit-permissions` and the plugin `useModule` resolution strategy. Test the documented Maven dependency as well.
- [ ] Verify inter-module POM dependencies resolve at the same tag version, rather than falling back to `0.1.0-SNAPSHOT` or the local Maven repository.
- [ ] Confirm the standalone example builds using JitPack only for MinecraftDevKit artifacts and that its generated metadata retains source `plugin.yml` values.
- [ ] Create the GitHub prerelease manually, link the changelog notes, and mark it as a prerelease. Do not claim artifacts are available until the JitPack build and consumer checks pass.

## Known external validation boundary

Local simulation can verify Gradle publications and POM coordinates using JitPack's documented `GROUP`, `ARTIFACT`, and `VERSION` environment variables. It cannot verify GitHub visibility, tag checkout, JitPack's artifact collection, remote marker availability, or actual public downloads. Those checks require the repository to be public, the tag to exist, and JitPack to complete the build.
