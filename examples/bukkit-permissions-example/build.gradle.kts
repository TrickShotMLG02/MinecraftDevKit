plugins {
    java
    id("com.trickshotmlg.devkit.bukkit-permissions") version "0.1.0-beta.1"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.trickshotmlg.devkit.examples"
version = "0.1.0-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    implementation("com.github.TrickshotMLG.MinecraftDevKit:devkit-permissions:0.1.0-beta.1")
}

minecraftDevKit {
    bukkitPermissions {
        catalogClass.set("com.trickshotmlg.devkit.examples.friendnet.FriendPermissions")
        catalogField.set("CATALOG")
    }
}

tasks.jar {
    // The deployable artifact is the self-contained Shadow JAR.
    enabled = false
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate(
        "org.yaml.snakeyaml",
        "com.trickshotmlg.devkit.examples.internal.snakeyaml"
    )
}

// Local Paper test server tasks are opt-in and do not affect the normal library/plugin build.
val paperMinecraftVersion = providers.gradleProperty("paperMinecraftVersion").orElse("1.21.8")
val paperServerDirectory = layout.buildDirectory.dir(paperMinecraftVersion.map { "devserver/paper-$it" })
val paperServerJar = paperServerDirectory.map { it.file("paper.jar") }
val paperPluginJar = tasks.shadowJar.flatMap { it.archiveFile }
val paperUtility = rootProject.layout.projectDirectory.file("../../tools/devservers/paper.ps1")
val paperPluginsManifest = rootProject.layout.projectDirectory.file("../../tools/devservers/paper-plugins.json")
val java21 = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
val powerShell = providers.gradleProperty("paperPowerShell").orElse("powershell.exe")

val provisionPaperServer = tasks.register<Exec>("provisionPaperServer") {
    group = "minecraft server"
    description = "Downloads and verifies the configured stable Paper server build."
    inputs.property("minecraftVersion", paperMinecraftVersion)
    inputs.property("powerShell", powerShell)
    inputs.file(paperUtility)
    outputs.file(paperServerJar)
    commandLine(
        powerShell.get(), "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", paperUtility.asFile.absolutePath,
        "-Action", "provision", "-MinecraftVersion", paperMinecraftVersion.get(),
        "-ServerDirectory", paperServerDirectory.get().asFile.absolutePath
    )
}

val deployToPaperServer = tasks.register<Exec>("deployToPaperServer") {
    group = "minecraft server"
    description = "Builds and deploys the shaded example plugin into the Paper test server."
    dependsOn(provisionPaperServer, tasks.shadowJar)
    inputs.file(paperPluginJar)
    inputs.file(paperUtility)
    inputs.property("powerShell", powerShell)
    outputs.file(paperServerDirectory.map { it.file("plugins/${paperPluginJar.get().asFile.name}") })
    commandLine(
        powerShell.get(), "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", paperUtility.asFile.absolutePath,
        "-Action", "deploy-plugin", "-ServerDirectory", paperServerDirectory.get().asFile.absolutePath,
        "-PluginJar", paperPluginJar.get().asFile.absolutePath
    )
}

val syncPaperTestPlugins = tasks.register<Exec>("syncPaperTestPlugins") {
    group = "minecraft server"
    description = "Downloads checksum-pinned extra test plugins from tools/devservers/paper-plugins.json."
    dependsOn(provisionPaperServer)
    inputs.file(paperPluginsManifest)
    inputs.file(paperUtility)
    inputs.property("powerShell", powerShell)
    commandLine(
        powerShell.get(), "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", paperUtility.asFile.absolutePath,
        "-Action", "sync-plugins", "-ServerDirectory", paperServerDirectory.get().asFile.absolutePath,
        "-PluginManifest", paperPluginsManifest.asFile.absolutePath,
        "-ReservedPluginFile", paperPluginJar.get().asFile.name
    )
}

fun Exec.configurePaperStart(debug: Boolean) {
    group = "minecraft server"
    description = if (debug) "Starts Paper with JDWP suspended on port 5005 for IntelliJ attachment."
        else "Starts the configured Paper test server."
    dependsOn(deployToPaperServer, syncPaperTestPlugins)
    commandLine(
        powerShell.get(), "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", paperUtility.asFile.absolutePath,
        "-Action", if (debug) "debug-server" else "run-server",
        "-ServerDirectory", paperServerDirectory.get().asFile.absolutePath,
        "-PaperJar", paperServerJar.get().asFile.absolutePath,
        "-JavaExecutable", java21.get().executablePath.asFile.absolutePath
    )
}

tasks.register<Exec>("runPaperServer") {
    configurePaperStart(debug = false)
}

tasks.register<Exec>("debugPaperServer") {
    configurePaperStart(debug = true)
}
