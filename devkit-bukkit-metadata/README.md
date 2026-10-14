# `devkit-bukkit-metadata`

This Java 17 module exports a platform-independent `PermissionCatalog` as a Bukkit-compatible YAML `permissions` section. It is intended for build tooling and can run without Bukkit, Paper, or a Minecraft server. It does not merge files, register permissions at runtime, or register Gradle tasks.

## Dependency

```kotlin
dependencies {
    implementation("com.trickshotmlg.devkit:devkit-bukkit-metadata:0.1.0-SNAPSHOT")
}
```

The module exposes `devkit-permissions` as an API dependency. SnakeYAML is an implementation dependency used for serialization; it is not part of the Java API.

## Usage

```java
import com.trickshotmlg.devkit.bukkit.metadata.BukkitPermissionYamlExporter;
import com.trickshotmlg.devkit.permissions.PermissionCatalog;

import java.nio.file.Files;
import java.nio.file.Path;

PermissionCatalog catalog = PluginPermissions.CATALOG;
String yaml = BukkitPermissionYamlExporter.export(catalog);
Files.writeString(Path.of("build/generated/plugin-permissions.yml"), yaml);
```

The document contains a top-level `permissions` mapping. It emits ordinary permission definitions first, then group definitions, preserving catalog registration order. Each definition includes its `default` value and includes its description when one was declared. `DefaultAccess` maps to Bukkit's `true`, `false`, `op`, and `not op` values.

Sections are omitted because they are organizational selections, not Bukkit permission nodes. Groups are emitted as actual permission nodes. Their child maps are flattened from `catalog.effectivePermissions(group)` in stable catalog resolution order. Every child value is `true`; excluded permissions are absent, including when they were reached through multiple inherited or direct grant paths. Empty groups omit `children`.

## Native permission semantics and limits

Bukkit child relationships are positive or negative assignments: a `true` child relation follows a parent's positive assignment, while a `false` relation inverts it. The exporter emits only positive child relations and never translates a model exclusion into a negative assignment. This means a group exclusion removes that node from this group's child map; it does not revoke an assignment from another group, attachment, default, or permission plugin.

The output declares default access metadata for both ordinary nodes and group nodes. Bukkit defaults can assign a permission independently of a group. Therefore an ordinary permission with `DefaultAccess.TRUE`, for example, may be granted even when it is absent from a group's effective catalog set. External permission managers and runtime attachments may also add or override assignments. The catalog is a declaration model, not a complete runtime authorization result, so flattening its groups cannot guarantee identical outcomes under every Bukkit permission setup.

The exporter intentionally does not use Bukkit API classes. A later Gradle integration can call this API to generate or merge metadata; this module currently returns YAML only.
