# devkit-paper-commands

Optional native Paper command registration for the existing shared `CommandDefinition` model. It uses Incendo Cloud's `PaperCommandManager` and Paper's command lifecycle/Brigadier registration. Build against Paper 1.20.6 or newer; this repository compiles and validates against Paper 1.21.11.

```java
PaperCommandAdapter commands = new PaperCommandAdapter(this, textProvider, Locale.ENGLISH);
commands.register(existingCommandDefinition);
```

Create and register the adapter during `JavaPlugin#onEnable`. Paper owns and refreshes the lifecycle registrations. Use `BukkitCommandAdapter` for the legacy Bukkit path on Spigot and Paper; choose exactly one backend for each root. Cloud-managed roots must not also be declared in plugin.yml or paper-plugin.yml. Typed command trees, help, permissions, suggestions, localization, and error handling are delegated to the same shared Cloud registrar used by the Bukkit adapter. Paper-specific API classes are isolated in this artifact.

The current MinecraftDevKit adapter deliberately registers at plugin enable. Its command handlers may depend on the `JavaPlugin`, runtime service container, Bukkit permission manager, and runtime sender types, which are unavailable during Paper bootstrap. Moving these existing command definitions into bootstrap would change their initialization contract; this module does not claim Cloud bootstrap command registration. Native Paper plugins should declare bootstrap-safe descriptors or datapack callbacks separately and initialize runtime commands once from `onEnable`.

## Optional permission-aware completion filtering

Paper 1.21.11's explicit completion traversal can return protected static literals even though the player-specific Brigadier tree and execution checks enforce permissions. The outgoing suggestion filter is an opt-in mitigation for that Paper behavior. It listens to `AsyncPlayerSendSuggestionsEvent`, removes only suggestions matching a protected literal in a registered MinecraftDevKit definition, and evaluates the same inherited permission and sender requirements as command visibility.

Native Paper backend:

```java
PaperCommandAdapter commands = new PaperCommandAdapter(this, textProvider, Locale.ENGLISH)
        .enablePermissionAwareCompletions();
commands.register(commandDefinition);
```

For a Bukkit-compatible Cloud command backend running on Paper, create the Paper-only filter separately and register the definition after the Bukkit adapter successfully registers it:

```java
BukkitCommandAdapter commands = new BukkitCommandAdapter(this, textProvider, Locale.ENGLISH);
commands.register(commandDefinition);

PaperPermissionSuggestionFilter filter =
        new PaperPermissionSuggestionFilter(this, Locale.ENGLISH);
filter.register(commandDefinition);
```

Keep the filter in an owned field and call `close()` during your normal shutdown. The native adapter owns and closes its filter automatically. The filter is disabled by default and has no Spigot effect; it is available only from the optional Paper artifact.

The Paper event does not identify which plugin supplied each suggestion, so register only roots/aliases whose effective command registration belongs to your plugin. A duplicate label within one filter is rejected; collisions with third-party command roots cannot be proven from this event, so avoid enabling the filter for an ambiguous root. It does not alter Brigadier or command execution. Dynamic suggestions are preserved and providers are not rerun. If the current path has an argument branch, text alone cannot reliably distinguish a same-text argument value from a literal, so that ambiguous response is left unchanged. Bukkit does not guarantee that player permission reads are safe on an asynchronous callback; the filter therefore skips asynchronous suggestion events and logs one warning. Paper 1.21.11's ordinary completion path observed in the protocol fixture fires this event synchronously. This is a scoped mitigation, not a guarantee against providers that run before the event or third-party plugins that later replace suggestions.
