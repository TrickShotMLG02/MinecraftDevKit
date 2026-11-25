# devkit-bukkit-commands

This Java 21 adapter connects platform-independent `devkit-commands` definitions to Incendo Cloud v2's legacy Bukkit integration. It delegates parsing, dispatch, suggestions, and native permission checks to Cloud and Bukkit. `LegacyPaperCommandManager` remains supported for Spigot and Paper consumers that want the legacy Bukkit registration path. Paper consumers that explicitly select native lifecycle/Brigadier registration can use the optional [`devkit-paper-commands`](../devkit-paper-commands/README.md) adapter with the same command definitions.

## Dependencies

```kotlin
repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    mavenCentral()
}

dependencies {
    implementation("com.trickshotmlg.devkit:devkit-bukkit-commands:<version>")
    compileOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
}
```

The adapter publishes `devkit-commands` and `org.incendo:cloud-paper:2.0.0` as API dependencies. It compiles against Spigot API 1.21.11. Keep the server API `compileOnly`; do not shade Bukkit/Spigot/Paper classes into the plugin. Spigot does not supply Adventure API classes, so package compatible Adventure runtime libraries for Spigot; Paper supplies them. Cloud is MIT-licensed. If you shade Cloud into your plugin, preserve its license notice and relocate it when multiple plugins may bundle their own Cloud version.

## Register commands

Construct one adapter during `JavaPlugin.onEnable()` and register a validated definition:

```java
public final class FriendNetPlugin extends JavaPlugin {
    private BukkitCommandAdapter commandAdapter;

    @Override
    public void onEnable() {
        TextProvider texts = ResourceBasedTextProvider.fromClasspath(
                getClass().getClassLoader(), "translations",
                List.of(Locale.US, Locale.GERMANY), Locale.US,
                MissingTranslationPolicy.SHOW_KEY,
                diagnostic -> getLogger().warning(diagnostic.toString()));

        commandAdapter = new BukkitCommandAdapter(this, texts, Locale.US);
        commandAdapter.register(FriendCommands.create());
    }
}
```

The adapter maps `Player` to `PLAYER`, console senders to `CONSOLE`, and other Bukkit senders to `OTHER`. Permission checks call `CommandSender.hasPermission(permission.node())`; inherited command requirements are combined by the shared model before reaching Bukkit. Player messages resolve the current `Player#getLocale()` at send time; console messages use the configured default locale. Components are sent through Adventure's `Audience` API when the sender provides it (as Paper does), and serialized to legacy section text for Bukkit senders such as Spigot's `Player`.

`BukkitArguments.player("target")` creates a strongly typed `Argument<Player>` using Cloud's native Bukkit player parser and suggestions. Other platform-specific argument types can be registered through the exposed `cloudManager()` and the Cloud parser registry, or represented as custom parsers in shared definitions.

## Registration and thread behavior

This adapter uses `LegacyPaperCommandManager` and registers commands from `onEnable()`. Cloud owns the native registration for the plugin lifecycle. Do not duplicate these command roots in `plugin.yml`; continue to use the permission metadata generator for permission declarations only. The adapter enables Cloud's native Brigadier integration when the server advertises that capability. On older Bukkit/Spigot servers without Brigadier, Cloud's legacy command and tab-completion behavior is used.

The default `ExecutionCoordinator.simpleCoordinator()` keeps handlers on the calling thread. Do not move Bukkit API calls into worker-thread command handlers or custom suggestion providers; asynchronous application work must return to the server thread before touching Bukkit state. Cloud's Brigadier suggestions are preferred when available; the adapter does not enable asynchronous legacy completions.

The adapter installs the shared localized error handler and generated help renderer once per manager. Usage/help visibility uses the same inherited permission and sender requirements as command execution. Unhandled execution errors are logged through the shared JDK logger and players receive a safe translated error message.

## Compatibility

The module is compiled against Spigot API 1.21.11 and Cloud Paper 2.0.0. It contains no server API classes in its artifact. Paper bootstrap registration, Velocity, Fabric, Forge, and NeoForge command integration are outside this module's current scope.
