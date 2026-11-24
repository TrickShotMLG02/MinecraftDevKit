# devkit-bukkit-lifecycle

Java 21 Bukkit/Spigot adapter for the platform-independent `devkit-lifecycle` module. It checks the traditional Bukkit primary server thread for start, reload, and close, logs lifecycle failures using the plugin logger, and closes the manager on plugin disable. It does not replace Paper bootstrap or other special lifecycle APIs.

```java
public final class ExamplePlugin extends JavaPlugin {
    private BukkitLifecycle lifecycle;

    @Override public void onEnable() {
        LifecycleManager services = LifecycleManager.builder()
            .service(CONFIG, context -> ExampleConfig.load(getDataFolder().toPath()))
            .service(DATABASE, context -> new ExampleDatabase(context.require(CONFIG)), CONFIG)
            .build();

        lifecycle = BukkitLifecycle.create(this, services);
        lifecycle.start(); // runs synchronously on Bukkit's primary server thread
    }

    @Override public void onDisable() {
        if (lifecycle != null) lifecycle.close();
    }
}
```

`BukkitLifecycle.close()` is idempotent, and the adapter also listens for its owning plugin's `PluginDisableEvent` as a backup. Startup failure is logged and rethrown so Bukkit does not leave the plugin appearing successfully enabled; the core manager first attempts rollback. Reload returns `LifecycleReloadResult`, logging each failed hook without claiming an atomic rollback. All lifecycle callbacks execute synchronously on the caller thread, which this adapter requires to be Bukkit's primary thread.

Use normal `onEnable`/`onDisable` for services requiring Bukkit runtime facilities. `onLoad` runs before ordinary plugin enable and is not used automatically. The module compiles against Spigot API 1.21.11 and targets its traditional single primary-thread model; live Spigot and Paper behavior is not verified here. It is not Folia-compatible; Folia entity and region ownership requires a separate adapter and is not modeled as a global-thread fallback here. The shared `devkit-lifecycle` module has no Bukkit dependency.
