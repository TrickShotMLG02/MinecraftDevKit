# devkit-paper-lifecycle

Optional Java 21 adapter for Paper bootstrap-phase registrations. The module depends on `devkit-bukkit-lifecycle` and Paper API at compile time; shared service startup and shutdown remain owned by the existing `LifecycleManager` and `BukkitLifecycle` adapters.

`PaperBootstrapLifecycle` currently exposes one bootstrap-safe hook: datapack discovery. It accepts `BootstrapContext` only at registration time and does not retain it. Paper owns the accepted lifecycle registration; the API provides no supported rollback/unregister operation, so failures are propagated and already accepted handlers are not claimed to be rolled back. Bootstrap callbacks must not start runtime services or access players, worlds, the Bukkit scheduler, storage, or GUI managers.

Native Paper plugins can use a normal `JavaPlugin` as `paper-plugin.yml`'s `main`, instantiate shared runtime services in `onEnable`, and delegate startup/shutdown to `BukkitLifecycle`. Bootstrap is an optional earlier phase, not a replacement service container. Folia is not supported.
