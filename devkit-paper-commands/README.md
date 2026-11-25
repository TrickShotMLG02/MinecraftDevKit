# devkit-paper-commands

Optional native Paper command registration for the existing shared `CommandDefinition` model. It uses Incendo Cloud's `PaperCommandManager` and Paper's command lifecycle/Brigadier registration. Build against Paper 1.20.6 or newer; this repository compiles and validates against Paper 1.21.11.

```java
PaperCommandAdapter commands = new PaperCommandAdapter(this, textProvider, Locale.ENGLISH);
commands.register(existingCommandDefinition);
```

Create and register the adapter during `JavaPlugin#onEnable`. Paper owns and refreshes the lifecycle registrations. Use `BukkitCommandAdapter` for the legacy Bukkit path on Spigot and Paper; choose exactly one backend for each root. Cloud-managed roots must not also be declared in plugin.yml or paper-plugin.yml. Typed command trees, help, permissions, suggestions, localization, and error handling are delegated to the same shared Cloud registrar used by the Bukkit adapter. Paper-specific API classes are isolated in this artifact. This module does not add bootstrap/plugin-loader metadata support.
