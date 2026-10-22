# MinecraftDevKit Bukkit Lifecycle Example

Standalone Java 21 Paper plugin demonstrating the public lifecycle and service APIs. From the repository root, publish modules to Maven Local and build the example independently:

```powershell
.\gradlew.bat publishToMavenLocal
Push-Location examples\bukkit-lifecycle-example
..\..\gradlew.bat clean build -PmdkRepository=mavenLocal
Pop-Location
```

Cloud registers `/mdklifecycle`; its root is intentionally absent from `plugin.yml`. Try:

- `/mdklifecycle status` — lifecycle state, deterministic startup order, and component states.
- `/mdklifecycle services` — each service state and a typed feature service lookup.
- `/mdklifecycle reload` — calls the configuration reload hook, then the feature hook. The example reports a partial result if a hook fails.

The service graph starts configuration before database, database and scheduler before the feature, and then closes in reverse dependency order. The feature owns a `TaskScope` and a cleanup callback; the database is an `AutoCloseable`. `BukkitLifecycle` checks that startup, reload, and shutdown run on the traditional Bukkit server thread and closes from `onDisable` (with an idempotent plugin-disable listener as a backup).

This is a build-verified example, not a live Paper test. It targets Paper 1.21.11, does not support Folia, and does not declare Cloud's command root in `plugin.yml`.
