# MinecraftDevKit Bukkit Commands Example

This is an independent JavaPlugin build demonstrating command, permission, reloadable YAML localization, and scanner APIs together. It resolves MinecraftDevKit artifacts through Maven Local and does not include Gradle project dependencies on the repository root.

## Prerequisites and build

- JDK 21.
- Paper 1.21.11 for the documented server check.
- Publish the current repository artifacts to Maven Local from the repository root.

From the repository root:

```powershell
.\gradlew.bat publishToMavenLocal
cd examples\bukkit-commands-example
.\gradlew.bat clean build
```

```sh
./gradlew publishToMavenLocal
cd examples/bukkit-commands-example
./gradlew clean build
```

The build compiles Java definitions, generates a text-reference manifest from the compiled command tree, strictly scans the English and German catalogs plus partial regional locales, generates and merges Bukkit permission metadata, and creates one deployable shaded plugin at `build/libs/minecraftdevkit-bukkit-commands-example-0.1.0-SNAPSHOT.jar`.

The dependencies are `com.trickshotmlg.devkit:devkit-bukkit-commands`, `devkit-localization`, `devkit-localization-yaml`, and `devkit-permissions`, all resolved from Maven Local. The Paper API is `compileOnly`. Cloud v2 and SnakeYAML are relocated into the plugin JAR. Adventure is supplied by Paper and excluded from the shaded JAR. Third-party notices and the Apache 2.0 license text are included in `META-INF`.

## Reloadable YAML localization

The plugin loads bundled resources from `src/main/resources/lang` and creates editable defaults under:

```text
plugins/MinecraftDevKitCommandsExample/lang/
├── en.yml
├── en-GB.yml
├── de.yml
└── de-AT.yml
```

`en-GB.yml` and `de-AT.yml` intentionally contain only overrides. A requested regional locale checks its exact external file, exact bundled file, then its parent locale, checking external before bundled at each locale. After requested parents, the configured global fallback is `en`, followed by the optional `TextRef` fallback and missing-key policy. External values replace bundled values only at the same locale and key. The caller supplies the sender locale; this example does not store per-player language preferences.

The same `LocalizationService` instance is passed to `BukkitCommandAdapter` and response helpers. YAML templates support MiniMessage formatting; `{player}` and other dynamic values are inserted as plain components, so text supplied by a player is not parsed as formatting. Editing an external file does not require rebuilding the plugin.

The service enables its optional file watcher and also exposes `reloadAsync()`. `/friends admin reload` requests a reload asynchronously, then reports the result on the server thread. If any source fails to parse, it reports the error and keeps the last valid catalog active. The plugin closes the service and watcher during shutdown.

To verify a live edit, start the example on a test server and change `plugins/MinecraftDevKitCommandsExample/lang/en.yml`, for example:

```yaml
friends:
  add:
    success: "<green>Friend {player} was added by an external override."
```

Run `/friends add Steve` as a player with the required permission. The changed text should appear without restart. To test manual reload instead of watcher detection, edit the file and run `/friends admin reload`; the confirmation reports the loaded entry and locale counts. To test rollback, temporarily break the YAML indentation, request reload, then run `/friends add Steve` again: the previous valid message should still display.

The build's scanner reads the same YAML resources without a server. Its strict locales are `en`, `en-GB`, `de`, and `de-AT`; parent-locale coverage is reported as informational. The example enables the Gradle scanner through `check`.

## Demonstrated API

The Java permission catalog includes individual permissions, child names, reusable nested sections, `DEFAULT`, `DEFAULT_EXTENDED`, `MODERATOR`, and `ADMIN_GROUP`, inherited grants, an exclusion, and a build-time `grantAllPermissions()` selection. `ADMIN_GROUP` has the actual Bukkit node `friendnet.admin`; the command tree uses the separately declared `friendnet.admin.commands` permission, which the group grants as a child. Sections are not exported as permission nodes.

`FriendCommands` defines:

```text
/friends
/friends list [page]
/friends add <player>
/friends remove <player>
/friends requests
/friends requests list
/friends requests accept <player>
/friends requests accept all
/friends requests deny <player>
/friends admin reload
```

Aliases are `/friend` and `/f`. The tree uses a typed Bukkit `Player` parser, a bounded integer argument with a default, parent permission requirements, player-only restrictions, generated usage and help, a localized explicit usage override, localized descriptions and errors, fallback-backed translation references, references without fallbacks, and one literal response. The request `accept all` literal takes precedence over the player argument branch. Example command handlers intentionally return illustrative messages rather than implementing friend storage.

The manifest is generated from `CommandDefinition.textReferences()`, framework `CommandErrorMessages`, and application response references. It is generated from compiled definitions without including processed resources in its classpath, so it does not form a cycle with `processResources`.

## Paper test

Use the local server provisioner documented in [`tools/devservers`](../../tools/devservers/README.md), download a Paper 1.21.11 server, and accept its EULA manually. Copy the shaded JAR from `build/libs` into that server's `plugins` directory, start Paper, then test in-game. Cloud registers the command roots; do not add these roots under `commands` in `plugin.yml`.

Test per-player locales with one English client and one German client. The same registered command should return localized descriptions, help, usage, and responses according to each player's locale. Console invocations of player-only commands should get the configured fallback-locale sender error.

## LuckPerms manual matrix

Create two or more test users and assign the generated group nodes with LuckPerms. Re-log or refresh permissions after changing assignments.

| Identity or assignment | Expected behavior |
| --- | --- |
| Normal non-operator, no LuckPerms assignment | `friendnet.default` has its Bukkit default grant. `/friends list`, `/friends add`, and `/friends requests list` are visible and executable. Extended and admin subcommands are hidden from generated help. |
| Operator | Bukkit's `op` default grants `friendnet.admin`; the flattened group children include `friendnet.admin.commands`, `friendnet.admin.reload`, and all ordinary permissions other than `friendnet.proxy.handshake`. `/friends admin reload` works. |
| LuckPerms user with `friendnet.default.extended=true` | Basic and extended permissions are available, including `friendnet.friends.list.others` and `friendnet.friends.requests.accept.all`. This group node does not imply a relationship by its dotted name; the exported group children grant its flattened selection. |
| LuckPerms user with `friendnet.moderator=true` | Inherited default and extended grants plus staff grants are available. `friendnet.friends.requests.accept.all` is excluded from this group's generated children, so `/friends requests accept all` is unavailable by this group alone. |
| Moderator plus independent `friendnet.friends.requests.accept.all=true` | The `accept all` command works. The catalog exclusion is not a denial and does not override another permission assignment. |
| Non-operator plus explicit `friendnet.admin.commands=true` | `/friends admin` becomes visible, but `/friends admin reload` still requires `friendnet.admin.reload`; the parent and child requirements combine with AND. |

For example:

```text
/lp user Alice permission set friendnet.default.extended true
/lp user Bob permission set friendnet.moderator true
/lp user Bob permission set friendnet.friends.requests.accept.all true
/lp user Charlie permission set friendnet.admin.commands true
```

Bukkit, operator defaults, permission attachments, and LuckPerms can combine grants from several sources. The example uses Bukkit's actual `hasPermission` result and does not treat exclusions as explicit denial.

## Artifact checks

The final JAR has one generated root `plugin.yml`, both translation resources, and relocated Cloud implementation classes. It has no Bukkit/Paper API classes and no Adventure classes. Cloud plugin registration owns command metadata; the permission generator adds only permission entries and leaves the source `plugin.yml` untouched.

This example targets traditional JavaPlugin lifecycle on Bukkit/Spigot-style servers and Paper's legacy manager with native Brigadier enabled when supported. It does not exercise Paper bootstrap-time registration or `paper-plugin.yml`.
