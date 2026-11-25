# Spigot and Paper runtime acceptance

This optional harness starts isolated, local Minecraft 1.21.11 server processes and installs the actual combined integration example, storage example, and a test-only runtime probe. It is deliberately outside the root Gradle build: `build`, `test`, `check`, `javadoc`, and `publishToMavenLocal` never start a server.

## Requirements

- Windows PowerShell 5.1 or PowerShell 7
- Java 21 on `PATH` (or pass `-JavaExecutable`)
- The root project and Maven Local dependencies can build
- A locally obtained Spigot 1.21.11 server jar and/or Paper 1.21.11 server jar, or opt-in `-Provision`
- A separate accepted EULA file for each manually supplied server jar
- To complete player-facing acceptance: an authorized Minecraft 1.21.11 client and at least one authenticated test account; two accounts are needed for multi-viewer isolation

The harness binds server and RCON listeners to `127.0.0.1`, keeps `online-mode=true`, and starts each server in a new run-owned directory. Manual mode never changes a supplied EULA file. The explicitly opted-in provisioning mode uses official SpigotMC BuildTools and the PaperMC Downloads Service, caches artifacts under ignored `build/runtime-acceptance/provision`, verifies SHA-256 on cache reuse/download, and writes `eula=true` only for the disposable server directories created by this harness. This test-server EULA change was explicitly authorized for this repository. It does not alter any other server. The harness never changes firewall rules or connects to public Minecraft servers.

## Configure and run

Supply server jars through arguments or environment variables. EULA arguments must point to existing files containing `eula=true`:

```powershell
$env:MDK_SPIGOT_SERVER_JAR = 'D:\test-artifacts\spigot-1.21.11.jar'
$env:MDK_SPIGOT_EULA_FILE = 'D:\test-artifacts\spigot-eula.txt'
$env:MDK_PAPER_SERVER_JAR = 'D:\test-artifacts\paper-1.21.11.jar'
$env:MDK_PAPER_EULA_FILE = 'D:\test-artifacts\paper-eula.txt'

./integration-tests/runtime/run.ps1 -Platform spigot
./integration-tests/runtime/run.ps1 -Platform paper
./integration-tests/runtime/run.ps1 -Platform all
```

Provision missing official artifacts, reusing checksum-verified cache entries:

```powershell
./integration-tests/runtime/run.ps1 -Platform all -Provision
```

Provisioning targets Minecraft 1.21.11 only. Spigot is built locally with official BuildTools (Git and Java 21 required); Paper is resolved from PaperMC's current v3 Downloads Service metadata. Both logs and provenance JSON are retained under `build/runtime-acceptance/provision`. If the verified cached Spigot build 4598 or Paper build 132 artifact exists, provisioning reuses it without network access or rebuilding. An invalid cache is not silently accepted. `-Provision` checks Java 21. Manual jar/EULA parameters and existing `MDK_*` environment variables continue to work without provisioning.

Or pass paths directly:

```powershell
./integration-tests/runtime/run.ps1 -Platform spigot `
  -SpigotServerJar 'D:\test-artifacts\spigot-1.21.11.jar' `
  -SpigotEulaFile 'D:\test-artifacts\spigot-eula.txt'
```

The runner publishes the current project to Maven Local and builds platform-specific artifacts before launch. `-SkipBuild` reuses already-built artifacts. `-StartupTimeoutSeconds` and `-ShutdownTimeoutSeconds` bound process waits. Each invocation uses fresh server data under `build/runtime-acceptance/<platform>/<run-id>/server`; the two server cycles within a run intentionally share that data directory to check restart persistence. Reports and logs remain under that run ID. Build outputs and acceptance results are ignored by Git.

When a server jar or accepted EULA is missing, the runner writes `NOT VERIFIED` results and exits with code 2. A provisioning failure is an explicit failed prerequisite and never falls back to a mock or alternate version. A startup/assertion failure is recorded as `FAIL`. The runner first requests an ordinary `stop`; if the exact server process it created does not exit before the configured timeout, it records shutdown as failed before force-cleaning up that process. It never terminates unrelated Java processes.

The harness helper regressions can be run on Windows PowerShell 5.1 or PowerShell 7 without starting a Minecraft server:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File integration-tests/runtime/tests/Test-Harness.ps1
```

The shaded storage and runtime probe standalone `test` tasks also depend on their shaded JAR and test migration discovery through a plugin-style URLClassLoader while the thread context loader cannot see the scripts.

## What the automated server probe covers

The probe is a test-only plugin, not a production publication. It loads actual common adapter classes and runs checks inside the selected server process. The combined example and storage example are also installed and invoked through local RCON. It records structured checks for:

- Common class loading and Paper GUI provider discovery (zero providers on Spigot; exactly one on Paper)
- Bukkit GUI construction, a real server inventory, common item metadata, and the Paper native Adventure-title path
- Typed lifecycle startup, service lookup, startup rollback, exactly-once resource cleanup, and disable cleanup
- Typed YAML defaults, save/reload, restart persistence, and bundled localization resolution
- SQLite JDBC, packaged Flyway migration resources, persisted schema/data, and restart behavior
- Cloud root/nested typed command registration and console dispatch
- Sync primary-thread execution, async-to-sync continuation, delayed cancellation, repeating task completion, and storage-result handoff
- Normal plugin disable, storage/task cleanup, server process exit, and localhost port release

Each cycle preserves stdout, stderr, the server log, and the probe JSON. The run writes JSON plus a Markdown report and a `checksums.json` sidecar covering the reports, environment, server logs, and cycle captures. Environment metadata includes the Git head, JVM string, Minecraft version check, and SHA-256 hashes for the server jar and installed plugin jars. Reports do not claim an upstream build identity the harness cannot independently attest.

Console/RCON dispatch is not player command acceptance. Server-created inventories and direct callbacks are not client-originated GUI tests. Those scenarios stay `NOT VERIFIED` until executed with a real authorized client.

## Manual player acceptance

Join each isolated server with an authenticated 1.21.11 test account. The harness reports the actual localhost port in `environment.json`. Use the same application commands on Spigot and Paper:

1. Run `/mdkintegration status`, `/mdkintegration menu`, `/mdkintegration reload`, and `/mdkstorage status` as a player. Check localized responses, command suggestions, nested syntax, and usage for invalid arguments.
2. Test an allowed and denied permission state for command and protected GUI actions. Use the example permission nodes shown by its README; grant permissions only in the temporary server instance.
3. Open the integration menu. Click settings, toggle and radio controls, save/reload, open player list/details, navigate backward, change pages, and close/reopen.
4. Try shift-click, number-key swap, offhand swap, double-click collection, drags across top slots, and bottom-inventory clicks. Confirm managed GUI contents cannot be taken or moved.
5. On Paper, verify the native Adventure GUI title/content path. On Spigot, verify the common legacy-serialized title and item text. Check Unicode, colors, lore, and formatting reset.
6. With two authorized players, open separate menus and verify viewer-specific localization and independent navigation/session state.
7. Join and leave while a player list is open, then verify item entries, counts, page bounds, and the displayed page. Test player-head rendering only with the expected network/profile availability recorded.
8. Restart the server through a clean `stop`, rerun `/mdkintegration status` and `/mdkstorage status`, and verify persisted config/database state.

Record client/version, account permissions, steps, and results in the relevant report. Do not use offline authentication or expose the temporary server publicly. Profile-service tests are network-dependent and must be separately identified.

### Guided interactive acceptance

Run the opt-in interactive mode separately for each platform. It performs the normal startup probes, keeps that isolated server online for a bounded player connection window and session window, then requests a graceful stop. The defaults are 180 seconds to connect and 900 seconds to complete the session; override them for short local checks.

```powershell
./integration-tests/runtime/run.ps1 -Platform spigot -Provision -Interactive
./integration-tests/runtime/run.ps1 -Platform paper -Provision -Interactive
```

For an already provisioned server, omit `-Provision` and supply the matching server jar and accepted EULA file as documented above. In Minecraft 1.21.11, join the `127.0.0.1:<serverPort>` endpoint printed by the runner and follow these steps:

1. Run `/devkitaccept start`, then `/mdkruntimeprobe`, `/mdkruntimeprobe status`, `/mdkruntimeprobe nested 42`, and `/mdkruntimeprobe nested nope`. The probe records root/status/nested callbacks from the actual Cloud sender bridge and records invalid-argument rejection only if the typed handler execution count remains unchanged.
2. Run `/mdkacceptcloud protected` while denied. Then run `/devkitaccept grant` followed by `/mdkacceptcloud protected`; Cloud must reject then execute the real permission-gated handler. The test-only attachment is revoked after execution and removed at completion, disconnect, or shutdown.
3. Run `/devkitaccept gui`. Click the emerald button, open the child and click Back, toggle the dye twice, select Beta then Alpha in the radio group, and open/confirm the confirmation menu. Open the pagination test with the book; click Next and verify Entry 46 appears first, then Previous and verify Entry 1 returns.
4. Run `/devkitaccept cancelgui` and click the red Cancel control. The report distinguishes cancellation from confirmation.
5. Run `/devkitaccept kit` to add a stone and paper to free slots and a stick to an empty offhand without replacing existing items. While a managed test inventory is open, perform Shift-left, Shift-right, number-key swap, double-click collection, offhand swap, and a drag that crosses from the GUI into the player inventory. Each transfer action is recorded only if the real Bukkit event was cancelled and the player's full inventory plus cursor remained unchanged after event processing. Pick up the paper before the double-click collection attempt.
6. Run `/devkitaccept status` as needed, then `/devkitaccept finish`. Finish succeeds only when every required observation above has passed. A typed command alone cannot advance an assertion.

The session is bound to the joining player's UUID. Other players' events are ignored. The in-game report is written to `plugins/MdkRuntimeProbe/interactive-results.json`; the harness copies it into `cycle-1-interactive-results.json`. If the client does not connect or the session does not finish, the report remains `NOT VERIFIED` and the server is stopped after the respective timeout. Existing automated mode and its two restart cycles are unchanged.

The guided probe directly exercises the common `GuiManager`, `GuiButton`, `ToggleButton`, `RadioGroup`, `PagedGui`, and `ConfirmGui` implementations. It does not verify client screenshots or visual fidelity, player-side tab-completion UI, multiple-player isolation, profile-service texture resolution, every click/drag gesture, or restart persistence through an interactive player session. Those remain `NOT VERIFIED`; a server-side assertion is not treated as client presentation evidence. Use the example's `/mdkintegration menu` for a manual broader localization/player-head check after completing the deterministic probe.

## Unit-test count reconciliation

The current root JUnit XML results are the authoritative local count for this checkout. The earlier 309/60 and 302 reports were not accompanied by a common archived XML baseline, so their seven-test difference cannot be assigned to exact test identities. Comparison of the platform-refactor source changes shows no deleted ordinary test classes; it added common GUI and Paper extension coverage, and the runtime probe has its own one-test suite. The optional network-storage integration source set is excluded from normal `test`. The discrepancy is therefore consistent with differing reporting scopes or a prior counting error, but the historical cause cannot be proven from the repository alone. Do not restore seven tests merely to match a reported number.

## Acceptance limitations

This harness targets only Spigot and Paper 1.21.11 on Java 21. It does not test Folia, other Minecraft versions, or MySQL/MariaDB. Without an authorized player client, all client-originated GUI, player command/permission, multi-viewer, and full profile interaction gates remain `NOT VERIFIED`. A successful server-side probe is necessary evidence but is not a substitute for those interactions.
