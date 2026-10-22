# MinecraftDevKit Bukkit Scheduler Example

Independent Java 21 Paper plugin showing immediate, delayed, repeating, scoped, and asynchronous tasks through the public `devkit-bukkit-scheduler` API. It uses Maven Local by default. JitPack mode must be selected explicitly and requires a published version; it never falls back to Maven Local.

From the repository root, publish the modules locally and build the independent plugin:

```powershell
.\gradlew.bat publishToMavenLocal
Push-Location examples\bukkit-scheduler-example
..\..\gradlew.bat clean build -PmdkRepository=mavenLocal
Pop-Location
```

Cloud registers `/mdkscheduler` (alias `/mdks`), so the root is intentionally not declared in `plugin.yml`.

## Commands

- `/mdkscheduler demo <player>` sends a message through a next-tick synchronous task and schedules another after 40 ticks.
- `/mdkscheduler repeat <player>` starts one scoped message every 20 ticks for the target player. Repeating it replaces that target's prior task.
- `/mdkscheduler cancel <player>` cancels the target's repeat.
- `/mdkscheduler async <player>` computes a harmless checksum off-thread and sends the result through a synchronous continuation after rechecking the player's online state.
- `/mdkscheduler status` reports active scoped work.

Tasks use server ticks, not exact wall-clock durations. At nominal 20 TPS, 20 ticks is about one second; lag can make elapsed time longer. The async computation only uses local values. Bukkit player lookup and messaging happen in the scheduled synchronous continuation. The plugin closes its scopes and scheduler in `onDisable`, and the adapter also observes its owning plugin's disable event.

## Manual Paper check

Install `build/libs/minecraftdevkit-bukkit-scheduler-example-0.1.0-SNAPSHOT.jar` into a Paper 1.21.11 test server that you are authorized to run. Accept Minecraft's EULA yourself before starting a server. Run the commands above, verify repeating messages stop after `cancel` or target disconnect, and verify async output arrives on the server without a blocking pause. Stop the plugin/server with tasks active and confirm no callbacks occur afterward. A successful build does not establish live server behavior.

The adapter targets Paper/Bukkit's traditional synchronous primary thread and is not Folia compatible. Async callbacks must not access Bukkit APIs unless those specific APIs document asynchronous safety.
