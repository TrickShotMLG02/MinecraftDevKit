# devkit-bukkit-scheduler

Java 21 Bukkit/Spigot adapter for the platform-independent `devkit-scheduler` module. It uses the plugin-owned native `BukkitScheduler`; it does not create an executor or a thread per task. The common implementation compiles against Spigot API 1.21.11; Spigot/Paper APIs are compile-only and are not bundled in the published module.

Spigot API compilation is checked, but live Spigot or Paper runtime behavior is not verified. Other server versions have not been verified. This adapter targets the traditional Bukkit primary server thread and is **not Folia compatible**. Folia's entity and region ownership requires a future adapter with explicit ownership targets, not fallback to its global scheduler.

## Setup

```kotlin
repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    mavenCentral()
}

dependencies {
    implementation("com.trickshotmlg.devkit:devkit-bukkit-scheduler:<version>")
    compileOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
}
```

## Plugin lifecycle and task usage

```java
public final class ExamplePlugin extends JavaPlugin {
    private BukkitTaskScheduler tasks;
    private TaskScope featureTasks;

    @Override public void onEnable() {
        tasks = BukkitTaskScheduler.create(this);
        featureTasks = tasks.scope();

        tasks.sync().later(TickDelay.of(20), () -> getLogger().info("One second at nominal TPS"));
        featureTasks.sync().repeat(TickDelay.ZERO, TickPeriod.of(20), context -> {
            updateServerState();
            if (shouldStop()) context.cancel();
        });

        tasks.async().submit(() -> loadDataFromDisk())
            .thenSync(data -> applyDataToServer(data))
            .onFailure(error -> getLogger().log(Level.SEVERE, "Load failed", error));
    }

    @Override public void onDisable() {
        if (featureTasks != null) featureTasks.close();
        if (tasks != null) tasks.close();
    }
}
```

`run` schedules as soon as Bukkit permits (synchronous work runs on a later server tick); `nextTick` requests a one-tick delay. Tick-based `later` and `repeat` remain available. Their `Duration` overloads round positive durations up to ticks at nominal 20 TPS; server lag increases wall time. Async callbacks must not touch Bukkit world, entity, inventory, or player state unless that specific API documents off-thread safety. `thenSync` performs the handoff without blocking and is cancelled with its result scope.

`BukkitPlayerTaskScopes.create(plugin, ownerScope, true)` provides stable child scopes keyed by player UUID and closes each scope on `PlayerQuitEvent`. Pass `false` when work should outlive disconnect; callers may still use `closePlayer(uuid)` explicitly. The helper retains UUIDs and scopes, never `Player` instances, and should be closed with its lifecycle owner.

The adapter registers a plugin-disable listener, closes its scheduler when that plugin disables, unregisters the listener on close, and releases its plugin reference. Explicit `onDisable` closure remains recommended. Calling the handle's `cancel()` keeps wrapper state consistent. If external code cancels tasks directly with `BukkitScheduler.cancelTasks(plugin)`, the wrapper cannot observe those external cancellations until the scheduler itself closes or a callback reaches the wrapper.

Native Bukkit task cancellation does not interrupt a callback already running. Do not schedule long-running work on the sync context. Async code uses Bukkit's native async scheduler and must be independently thread-safe. External cancellation does not necessarily interrupt a running callback either.

The adapter does not declare or claim Folia support. A Folia implementation must add location/entity ownership scheduling APIs and cannot treat a global scheduler as universally safe for server state.
