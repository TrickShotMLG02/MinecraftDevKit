# MinecraftDevKit Bukkit Permissions Example

This standalone Gradle build can consume either the current development artifacts from Maven Local or a tagged release from JitPack. It never falls back between repositories.

## Repository mode

After publishing the repository modules locally, build against the matching `0.1.0-SNAPSHOT` artifacts:

```powershell
.\gradlew.bat clean build -PmdkRepository=mavenLocal
```

```sh
./gradlew clean build -PmdkRepository=mavenLocal
```

For a published Git tag, select JitPack and specify the exact tag version:

```powershell
.\gradlew.bat clean build -PmdkRepository=jitpack -PmdkVersion=0.1.0-beta.1
```

```sh
./gradlew clean build -PmdkRepository=jitpack -PmdkVersion=0.1.0-beta.1
```

In local mode, both the plugin implementation and libraries use group `com.trickshotmlg.devkit` at `0.1.0-SNAPSHOT`. JitPack mode uses `com.github.TrickshotMLG.MinecraftDevKit` at the requested tag. The Gradle plugin ID is mapped directly to the implementation module in `settings.gradle.kts`, since JitPack plugin marker resolution is not assumed.

This is a standalone Gradle build for a minimal Bukkit/Paper plugin. It declares a realistic permission catalog in Java and packages the generated `plugin.yml` together with runtime libraries in one deployable JAR.

It is intentionally outside the root multi-project build. It demonstrates traditional Bukkit/Spigot-style `plugin.yml`; it does not target Paper's separate `paper-plugin.yml` format.

## Prerequisites

- JDK 21 (Gradle toolchain and the Paper API target).
- Either the current MinecraftDevKit modules published to Maven Local, or a published JitPack tag. Remote mode cannot resolve until that tag exists and JitPack reports a successful build.
- A Paper 1.21.8 test server for the documented API target.

Build the standalone example in the selected repository mode using the commands above. JitPack mode does not fall back to Maven Local.

```powershell
cd examples\bukkit-permissions-example
.\gradlew.bat clean build -PmdkRepository=mavenLocal
```

```sh
cd examples/bukkit-permissions-example
sh ./gradlew clean build -PmdkRepository=mavenLocal
```

The deployable shaded plugin is `build/libs/minecraftdevkit-bukkit-permissions-example-0.1.0-SNAPSHOT.jar`.

## How it works

- `FriendPermissions` declares individual permissions, child names, nested sections, and the `DEFAULT`, `DEFAULT_EXTENDED`, `MODERATOR`, and `ADMIN` groups. Its public static `CATALOG` field is the Gradle plugin input.
- The `com.trickshotmlg.devkit.bukkit-permissions` plugin loads that compiled field in an isolated worker and merges exported metadata into `src/main/resources/plugin.yml`.
- The Paper API is `compileOnly`; it is not bundled.
- `devkit-permissions` is an implementation dependency and its runtime dependency graph is included in the Shadow JAR. The Paper API remains server-provided. SnakeYAML packages are relocated when present to reduce collisions with server/plugin classpaths.
- The ordinary `jar` task is disabled so `build` produces one deployable shaded JAR rather than a thin JAR alongside it.

The generator writes to `build/generated/minecraftdevkit/resources/plugin.yml`; it does not change the source descriptor. YAML comments and formatting can be normalized by serialization. Generated group children are flattened positive grants. They do not create group-to-group permission relationships, and exclusions are omitted rather than emitted as negative children.

Catalog static initialization runs during the build. Keep it lightweight and independent of Bukkit runtime state.

## Testing on Paper

The repository includes a Paper server provisioner and IntelliJ run/debug profiles. See [`tools/devservers/README.md`](../../tools/devservers/README.md) for setup. From this example directory, run `.\gradlew.bat provisionPaperServer`, review and accept the Minecraft EULA manually, then use `.\gradlew.bat runPaperServer`. The run task builds and deploys the plugin automatically.

1. Start Paper 1.21.8. Confirm that the startup log reports the catalog node and group counts.
2. Join as a normal non-operator and run `/mdkperms`. The player should have the `friendnet.default` node and its basic children, but not the extended or staff permissions.
3. Grant operator status in the server console (or test as an operator) and run `/mdkperms` again. Bukkit's `op` default should grant `friendnet.admin`, whose positive child declarations cover every catalog permission except `friendnet.proxy.handshake`.
4. Use LuckPerms for explicit group-node assignments, for example:

   ```text
   /lp user Alice permission set friendnet.default.extended true
   /lp user Bob permission set friendnet.moderator true
   ```

   Relog or refresh permissions as needed, then run `/mdkperms`. The extended node grants the flattened `DEFAULT` and `EXTENDED` selections. The moderator node includes inherited extended grants and staff grants, while its generated children omit `friendnet.friends.requests.accept.all`.

5. Verify the exclusion is not a global denial by assigning that node independently:

   ```text
   /lp user Bob permission set friendnet.friends.requests.accept.all true
   ```

   `/mdkperms check friendnet.friends.requests.accept.all` may now report `TRUE` despite the moderator group's omission. This is expected: an exclusion removes a grant from that group; it does not override independent LuckPerms assignments or other Bukkit permission sources.

`/mdkperms check <permission.node>` checks any node through Bukkit's `Player.hasPermission` and is useful for inspecting additional explicit assignments.

## Manual test matrix

| Test identity / setup | Expected checks |
| --- | --- |
| Normal non-operator, no permission plugin assignment | `friendnet.default` and basic friend/request permissions are true; extended, moderator, admin, and staff checks are false. |
| Operator | `friendnet.admin` and all ordinary catalog permissions other than `friendnet.proxy.handshake` are true through the admin node. The proxy handshake remains false absent an independent assignment. |
| LuckPerms user assigned `friendnet.default.extended` | Extended group node and its flattened basic + extended grants are true. Group inheritance grants effective permissions; it does not itself add a separate parent-node child relationship. |
| LuckPerms user assigned `friendnet.moderator` | Basic, extended, and staff permissions are true except `friendnet.friends.requests.accept.all`, which the moderator definition excludes. |
| Moderator with explicit `friendnet.friends.requests.accept.all=true` | The check becomes true from the independent assignment. The group exclusion is not an explicit deny. |
| Non-operator with explicit `friendnet.proxy.handshake=true` | The check is true even though ADMIN excludes that node from its own grants; exclusions do not revoke external grants. |

Native Bukkit defaults, operator status, permission attachments, and LuckPerms may combine to produce results beyond the catalog's group sets. The command reports Bukkit's actual effective check, not an independent MinecraftDevKit authorization decision.

## Dependency coordinates

The independent build resolves the plugin implementation module and permission library from exactly one selected MinecraftDevKit repository. In JitPack mode, `pluginManagement.resolutionStrategy` maps the plugin ID directly to `com.github.TrickshotMLG.MinecraftDevKit:devkit-gradle-plugin:<tag>`. This avoids relying on the plugin marker artifact, whose group is the plugin ID rather than JitPack's multi-module repository group. In Maven Local mode, the plugin and library use the repository's `com.trickshotmlg.devkit:...:0.1.0-SNAPSHOT` coordinates. No `project(...)` dependencies point back into the root build.
