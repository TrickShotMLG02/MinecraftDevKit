# MinecraftDevKit Bukkit Configuration Example

Independent Java 21 Paper plugin build demonstrating `devkit-config` and `devkit-config-yaml`. It uses Maven Local by default; remote verification requires the explicit `-PmdkRepository=jitpack -PmdkVersion=<tag>` mode and never falls back between repositories.

From the repository root, publish locally and build the independent example:

```powershell
.\gradlew.bat publishToMavenLocal
cd examples\bukkit-config-example
..\..\gradlew.bat clean build -PmdkRepository=mavenLocal
```

The plugin uses a Java-defined schema in `FriendConfig`, loads `config.yml` from its data folder, and registers `/mdkconfig show`, `/mdkconfig setmax <value>`, `/mdkconfig reload`, and `/mdkconfig save` through the existing MinecraftDevKit Cloud/Bukkit adapter. Cloud owns command registration, so the command root is deliberately absent from `plugin.yml`. `setmax` changes memory only. Reload and save use a plugin-owned bounded single-thread executor; responses are scheduled onto Bukkit's main thread.

## Manual Paper test

Install `build/libs/minecraftdevkit-bukkit-config-example-0.1.0-SNAPSHOT.jar` in a Paper 1.21.8 server's `plugins` folder. Accept Minecraft's EULA yourself before starting a test server. Inspect `plugins/MinecraftDevKitConfigExample/config.yml` after startup.

1. Run `/mdkconfig show`; expect `friends.max=100`, requests enabled, and `PT5M` timeout.
2. Run `/mdkconfig setmax 250`, then `/mdkconfig show`; 250 is active in memory but not on disk.
3. Run `/mdkconfig save`, restart, and confirm 250 persists.
4. Edit `friends.max` to 0, then `/mdkconfig reload`; validation should fail and the active value should remain 250.
5. Set max to 300 with `setmax`, edit YAML to 350, then run reload. It should reject the dirty-state conflict.
6. To test migration, stop the server and change the file to version 1 with `friends.max-count: 75` and the request settings. Start the plugin; it should load 75 as a dirty migrated value. `/mdkconfig save` creates a `.v1.bak` and writes schema version 2 using `friends.max`.
7. Add an unknown nested field and save a known setting; the unknown field should remain. Comments and formatting are normalized by the explicit full-document save operation.

The example uses Paper's JavaPlugin lifecycle. The configuration modules themselves do not depend on Paper. A successful Gradle build does not imply that this plugin has been tested on a live server.
