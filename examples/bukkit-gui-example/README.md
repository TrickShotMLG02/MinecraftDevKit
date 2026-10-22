# Bukkit GUI Example

This standalone Java 21 Paper plugin demonstrates `devkit-bukkit-gui` as an ordinary Maven consumer. It uses the existing Cloud command adapter and permission metadata Gradle plugin. The `mdkgui` command root is registered by Cloud and is intentionally absent from `plugin.yml`.

## Build

From the repository root, publish current artifacts locally and build the independent project:

```powershell
.\gradlew.bat publishToMavenLocal
.\gradlew.bat -p examples\bukkit-gui-example clean build -PmdkRepository=mavenLocal
```

Remote mode is explicit and requires an existing release tag:

```powershell
.\gradlew.bat -p examples\bukkit-gui-example build -PmdkRepository=jitpack -PmdkVersion=0.1.0-beta.1
```

## Demonstrated menus

- `/mdkgui` opens a localized main menu with a patterned background.
- The online player list uses a generic paginated view, page indicator, empty state, and join/quit refresh subscriptions.
- Player details demonstrate navigation history and back behavior.
- Settings demonstrate session-local toggles and a typed exclusive radio selection.
- Skull showcase demonstrates an online profile, async offline profile placeholder, texture hash, and Base64 texture property.
- The confirmation menu runs its callback once and returns to the previous menu on cancel.

The example uses sample texture identifiers. Replace them with texture hashes from skins you are authorized to use. Offline profile updates are requested through Paper's asynchronous profile API; inventory work is scheduled back to the server thread. Skin availability depends on Mojang profile services and cache state.

## Manual Paper check

Use a Paper 1.21.11 server with Java 21 and follow Minecraft's EULA requirements manually. Copy the built `build/libs/minecraftdevkit-bukkit-gui-example-0.1.0-SNAPSHOT.jar` to `plugins/`, start the server, and grant/test `mdkgui.open` as desired.

1. Run `/mdkgui` and verify the title follows the player's client locale (`en` or `de`).
2. Open the online list; join or disconnect a second test player and verify the listing/page totals update without reopening.
3. Open a player detail menu, press Back, and verify the prior list page is restored.
4. Toggle both settings and choose each radio option; verify visuals and callbacks update once.
5. Open the skull showcase and verify the offline head resolves after its placeholder. Custom textures may be unavailable if the sample hash is not a live texture.
6. Open the confirmation dialog; cancel returns to the prior menu. Confirm sends one response and closes the GUI.
7. Try shift-click, number-key swap, offhand swap, double-click, drag, and cursor placement against the menu. No displayed GUI item should move into the player inventory.
8. Disable the plugin/server and verify no GUI tasks/listeners remain active.

No live Paper server test is claimed by the repository build checks.
