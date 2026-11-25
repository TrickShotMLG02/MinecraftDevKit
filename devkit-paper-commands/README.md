# devkit-paper-commands

Optional native Paper command registration for the existing shared `CommandDefinition` model. It uses Incendo Cloud's `PaperCommandManager` and Paper's command lifecycle/Brigadier registration. Build against Paper 1.20.6 or newer; this repository compiles and validates against Paper 1.21.11.

```java
PaperCommandAdapter commands = new PaperCommandAdapter(this, textProvider, Locale.ENGLISH);
commands.register(existingCommandDefinition);
```

Create and register the adapter during `JavaPlugin#onEnable`. Paper owns and refreshes the lifecycle registrations. Use `BukkitCommandAdapter` for the legacy Bukkit path on Spigot and Paper; choose exactly one backend for each root. Cloud-managed roots must not also be declared in plugin.yml or paper-plugin.yml. Typed command trees, help, permissions, suggestions, localization, and error handling are delegated to the same shared Cloud registrar used by the Bukkit adapter. Paper-specific API classes are isolated in this artifact.

The current MinecraftDevKit adapter deliberately registers at plugin enable. Its command handlers may depend on the `JavaPlugin`, runtime service container, Bukkit permission manager, and runtime sender types, which are unavailable during Paper bootstrap. Moving these existing command definitions into bootstrap would change their initialization contract; this module does not claim Cloud bootstrap command registration. Native Paper plugins should declare bootstrap-safe descriptors or datapack callbacks separately and initialize runtime commands once from `onEnable`.
