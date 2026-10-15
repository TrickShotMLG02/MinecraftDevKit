# Local Minecraft test servers

The first provisioner targets Paper because the repository currently has a Bukkit/Paper example. Other server families need their own installers and metadata; Fabric, Forge, NeoForge, Velocity, and Paper plugin/mod loading are not interchangeable.

## Paper test server

From `examples/bukkit-permissions-example`, with PowerShell available and JDK 21 installed:

```powershell
.\gradlew.bat provisionPaperServer
```

This downloads the latest stable Paper build for Minecraft 1.21.8 (or `-PpaperMinecraftVersion=...`) using PaperMC's official downloads service and validates the published SHA-256 checksum. The server is created at `examples/bukkit-permissions-example/build/devserver/paper-<version>/`. The ordinary build does not download or start a server.

Provisioning writes `eula=false` if no EULA file exists. Read and accept the [Minecraft EULA](https://www.minecraft.net/eula) yourself before changing it to `eula=true`. The run tasks stop with an actionable message until this is done.

Build and deploy the example plugin, then start the server:

```powershell
.\gradlew.bat deployToPaperServer
.\gradlew.bat runPaperServer
```

`runPaperServer` builds the Shadow JAR and deploys it before starting Paper. Server files and worlds remain under the ignored build directory. Stop the server with `stop` in its console; do not delete the directory while it is running.

### Add test plugins

Edit `tools/devservers/paper-plugins.json` with JAR downloads you trust. Every entry must include the final file name, direct URL, and expected SHA-256:

```json
[
  {
    "fileName": "TestPlugin.jar",
    "url": "https://downloads.example.org/TestPlugin.jar",
    "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  }
]
```

Then run `syncPaperTestPlugins`. A mismatch fails without replacing the destination JAR. The manifest starts empty; no third-party plugin is downloaded by default. Downloaded plugins execute code inside the test server, so only use sources and artifacts you trust. Remove stale JARs manually from that server's `plugins` folder.

## IntelliJ run and debug profiles

The shared project run configurations are in the repository `.run` directory. Open the root repository in IntelliJ and import/refresh Gradle. Also link `examples/bukkit-permissions-example` as a Gradle project (Gradle tool window **+** / **Link Gradle Project**) so IntelliJ indexes the plugin sources for breakpoints. Run **Paper Test Server** for normal startup. For plugin breakpoints, first run **Paper Server for Debug**; it starts the server suspended on JDWP port 5005. Then start **Attach Paper Debugger**. Both server profiles build, deploy the example, and sync checksum-pinned plugins automatically.

The debug JVM listens on localhost via JDWP. `-PpaperMinecraftVersion=<version>` selects another Paper server release; use `--rerun-tasks` on `provisionPaperServer` when you want to refresh the stable server build for an existing version. On systems using PowerShell 7, pass `-PpaperPowerShell=pwsh`.

The example targets Paper 1.21.8 and Java 21. Newer Minecraft/Paper releases may require a newer Java toolchain; use a matching JDK and rebuild the example before changing the server version.

## Build lifecycle and future platforms

`deployToPaperServer`, `runPaperServer`, and `debugPaperServer` are opt-in Gradle tasks in the standalone example. They can be invoked from another local build lifecycle by calling the example's Gradle Wrapper; a Maven lifecycle binding is not configured in this repository. Server downloads stay out of normal CI and Maven dependencies remain the source for library artifacts.

This utility currently provisions Paper only. Later platform profiles should each own their server installer, runtime requirements, and plugin/mod download manifest rather than assuming a Bukkit plugin directory works for mod loaders.
