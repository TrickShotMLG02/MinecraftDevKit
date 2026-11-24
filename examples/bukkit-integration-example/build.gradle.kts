plugins {
    java
    id("com.trickshotmlg.devkit.bukkit-permissions") version "0.1.0-SNAPSHOT"
    id("com.trickshotmlg.devkit.localization-scan") version "0.1.0-SNAPSHOT"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.trickshotmlg.devkit.examples"
version = "0.1.0-SNAPSHOT"
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

val mode = providers.gradleProperty("mdkRepository").orElse("mavenLocal").get().lowercase()
val mdkVersion = when (mode) {
    "mavenlocal" -> "0.1.0-SNAPSHOT"
    "jitpack" -> providers.gradleProperty("mdkVersion").get()
    else -> throw GradleException("Unsupported -PmdkRepository='$mode'; use mavenLocal or jitpack")
}
val mdkGroup = if (mode == "mavenlocal") "com.trickshotmlg.devkit" else "com.github.TrickShotMLG02.MinecraftDevKit"
val serverPlatform = providers.gradleProperty("mdkPlatform").orElse("paper").get().lowercase()
if (serverPlatform !in setOf("paper", "spigot")) {
    throw GradleException("Unsupported -PmdkPlatform='$serverPlatform'; use paper or spigot")
}

dependencies {
    if (serverPlatform == "paper") {
        compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
        implementation("$mdkGroup:devkit-paper-gui:$mdkVersion")
    } else {
        compileOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
    }
    implementation("$mdkGroup:devkit-bukkit-lifecycle:$mdkVersion")
    implementation("$mdkGroup:devkit-bukkit-scheduler:$mdkVersion")
    implementation("$mdkGroup:devkit-bukkit-gui:$mdkVersion")
    implementation("$mdkGroup:devkit-bukkit-commands:$mdkVersion")
    implementation("net.kyori:adventure-text-serializer-legacy:4.26.1")
    implementation("$mdkGroup:devkit-localization-yaml:$mdkVersion")
    implementation("$mdkGroup:devkit-config-yaml:$mdkVersion")
    implementation("$mdkGroup:devkit-permissions:$mdkVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

minecraftDevKit {
    bukkitPermissions {
        catalogClass.set("com.trickshotmlg.devkit.examples.integration.IntegrationPermissions")
        catalogField.set("CATALOG")
    }
}

devkitLocalizationScan {
    translationsDirectory.set(layout.projectDirectory.dir("src/main/resources/lang"))
    referenceManifest.set(layout.buildDirectory.file("generated/minecraftdevkit/text-references.json"))
    baseLocale.set("en")
    requiredLocales.set(listOf("en", "de"))
    defaultFallbackLocale.set("en")
    defaultFallbackEnabled.set(true)
    failOn.set(listOf("MISSING_FALLBACK", "MISSING_TRANSLATION", "MISSING_LOCALE_COVERAGE", "PLACEHOLDER_MISMATCH", "INVALID_RESOURCE", "DUPLICATE_REFERENCE"))
}

val generateTextReferenceManifest = tasks.register<JavaExec>("generateTextReferenceManifest") {
    group = "minecraft dev kit"
    description = "Builds the localization manifest from the compiled integration example."
    dependsOn(tasks.named("compileJava"))
    val main = sourceSets.main.get()
    classpath = files(main.output.classesDirs, main.compileClasspath, configurations.runtimeClasspath)
    mainClass.set("com.trickshotmlg.devkit.examples.integration.GenerateTextManifest")
    val manifest = layout.buildDirectory.file("generated/minecraftdevkit/text-references.json")
    outputs.file(manifest)
    argumentProviders.add(CommandLineArgumentProvider { listOf(manifest.get().asFile.absolutePath) })
}
tasks.named("scanDevKitTranslations") { dependsOn(generateTextReferenceManifest) }
tasks.named("check") { dependsOn("scanDevKitTranslations") }
tasks.test { useJUnitPlatform() }
tasks.jar { enabled = false }
tasks.shadowJar {
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    if (serverPlatform == "paper") {
        dependencies {
            exclude(dependency("net.kyori:adventure-api:.*"))
            exclude(dependency("net.kyori:adventure-key:.*"))
        }
    }
    relocate("org.incendo.cloud", "com.trickshotmlg.devkit.examples.internal.cloud")
    relocate("io.leangen.geantyref", "com.trickshotmlg.devkit.examples.internal.geantyref")
    relocate("org.yaml.snakeyaml", "com.trickshotmlg.devkit.examples.internal.snakeyaml")
    relocate("net.kyori.adventure.text.minimessage", "com.trickshotmlg.devkit.examples.internal.minimessage")
    relocate("com.trickshotmlg.devkit.permissions", "com.trickshotmlg.devkit.examples.internal.permissions")
    from(rootProject.file("../../THIRD-PARTY-NOTICES.md")) { into("META-INF"); rename { "THIRD-PARTY-NOTICES.md" } }
    from(rootProject.file("../../THIRD-PARTY-LICENSES/LICENSE-APACHE-2.0.txt")) { into("META-INF/licenses") }
    from(rootProject.file("../../THIRD-PARTY-LICENSES/LICENSE-MIT-ADVENTURE.txt")) { into("META-INF/licenses") }
}
