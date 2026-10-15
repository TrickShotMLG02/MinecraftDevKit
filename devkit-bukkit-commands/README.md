# devkit-bukkit-commands

This Java 21 adapter connects platform-independent `devkit-commands` definitions to Incendo Cloud v2's Bukkit/Paper integration. It delegates parsing, dispatch, suggestions, and native permission checks to Cloud and Bukkit. It does not implement a second command parser or player permission store.

## Dependencies

```kotlin
repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    implementation("com.trickshotmlg.devkit:devkit-bukkit-commands:<version>")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}
```

The adapter publishes `devkit-commands` and `org.incendo:cloud-paper:2.0.0` as API dependencies. Keep the server API `compileOnly`; do not shade Bukkit/Paper classes into the plugin. Cloud is MIT-licensed. If you shade Cloud into your plugin, preserve its license notice and relocate it when multiple plugins may bundle their own Cloud version.

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

The adapter maps `Player` to `PLAYER`, console senders to `CONSOLE`, and other Bukkit senders to `OTHER`. Permission checks call `CommandSender.hasPermission(permission.node())`; inherited command requirements are combined by the shared model before reaching Bukkit. Player messages resolve the current `Player#getLocale()` at send time; console messages use the configured default locale. Components are delivered through Bukkit/Paper's Adventure audience API.

`BukkitArguments.player("target")` creates a strongly typed `Argument<Player>` using Cloud's native Bukkit player parser and suggestions. Other platform-specific argument types can be registered through the exposed `cloudManager()` and the Cloud parser registry, or represented as custom parsers in shared definitions.

## Registration and thread behavior

This adapter uses `LegacyPaperCommandManager` and registers commands from `onEnable()`. Cloud owns the native registration for the plugin lifecycle. Do not duplicate these command roots in `plugin.yml`; continue to use the permission metadata generator for permission declarations only. The adapter enables Cloud's native Brigadier integration when the server advertises that capability. On older Bukkit/Spigot servers without Brigadier, Cloud's legacy command and tab-completion behavior is used.

Modern Paper's bootstrap-time `PaperCommandManager` registration is not wrapped yet. The legacy manager remains the supported JavaPlugin workflow and can also run on Paper. The default `ExecutionCoordinator.simpleCoordinator()` keeps handlers on the calling thread. Do not move Bukkit API calls into worker-thread command handlers or custom suggestion providers; asynchronous application work must return to the server thread before touching Bukkit state. Cloud's Brigadier suggestions are preferred when available; the adapter does not enable asynchronous legacy completions.

The adapter installs the shared localized error handler and generated help renderer once per manager. Usage/help visibility uses the same inherited permission and sender requirements as command execution. Unhandled execution errors are logged through the shared JDK logger and players receive a safe translated error message.

## Compatibility

The module is compiled against Paper API 1.21.11 and Cloud Paper 2.0.0/Core 2.1.0. It contains no server API classes in its artifact. `paper-plugin.yml`, Paper bootstrap registration, Velocity, Fabric, Forge, and NeoForge command integration are outside this module's current scope.
