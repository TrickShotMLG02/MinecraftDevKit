plugins {
    java
    id("com.trickshotmlg.devkit.bukkit-permissions") version "0.1.0-SNAPSHOT"
    id("com.trickshotmlg.devkit.localization-scan") version "0.1.0-SNAPSHOT"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.trickshotmlg.devkit.examples"
version = "0.1.0-SNAPSHOT"

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

val mdkRepository = providers.gradleProperty("mdkRepository").orElse("mavenLocal").get().lowercase()
val mdkVersion = when (mdkRepository) {
    "mavenlocal" -> "0.1.0-SNAPSHOT"
    "jitpack" -> providers.gradleProperty("mdkVersion").get()
    else -> throw GradleException("Unsupported -PmdkRepository='$mdkRepository'; use mavenLocal or jitpack")
}
val mdkGroup = if (mdkRepository == "mavenlocal") "com.trickshotmlg.devkit"
    else "com.github.TrickshotMLG.MinecraftDevKit"

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("$mdkGroup:devkit-bukkit-commands:$mdkVersion")
    implementation("$mdkGroup:devkit-localization:$mdkVersion")
    implementation("$mdkGroup:devkit-localization-yaml:$mdkVersion")
    implementation("$mdkGroup:devkit-permissions:$mdkVersion")
}

minecraftDevKit {
    bukkitPermissions {
        catalogClass.set("com.trickshotmlg.devkit.examples.friendnet.FriendPermissions")
        catalogField.set("CATALOG")
    }
}

devkitLocalizationScan {
    translationsDirectory.set(layout.projectDirectory.dir("src/main/resources/lang"))
    referenceManifest.set(layout.buildDirectory.file("generated/minecraftdevkit/text-references.json"))
    baseLocale.set("en")
    requiredLocales.set(listOf("en", "en-GB", "de", "de-AT"))
    defaultFallbackLocale.set("en")
    defaultFallbackEnabled.set(true)
    failOn.set(listOf("MISSING_FALLBACK", "MISSING_TRANSLATION", "MISSING_LOCALE_COVERAGE",
        "PLACEHOLDER_MISMATCH", "INVALID_RESOURCE", "DUPLICATE_REFERENCE"))
}

val generateTextReferenceManifest = tasks.register<JavaExec>("generateTextReferenceManifest") {
    group = "minecraft dev kit"
    description = "Builds the localization manifest from the compiled command definitions."
    dependsOn(tasks.named("compileJava"))
    val main = sourceSets.main.get()
    // Include compile-only platform APIs for loading typed command definitions, but avoid
    // sourceSets.main.runtimeClasspath's processed resources to prevent a processResources cycle.
    classpath = files(main.output.classesDirs, main.compileClasspath, configurations.runtimeClasspath)
    mainClass.set("com.trickshotmlg.devkit.examples.friendnet.GenerateTextManifest")
    val manifest = layout.buildDirectory.file("generated/minecraftdevkit/text-references.json")
    outputs.file(manifest)
    argumentProviders.add(CommandLineArgumentProvider { listOf(manifest.get().asFile.absolutePath) })
}

tasks.named("scanDevKitTranslations") { dependsOn(generateTextReferenceManifest) }
tasks.named("check") { dependsOn("scanDevKitTranslations") }

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    // Adventure API is supplied by Paper; keep MiniMessage bundled and isolated for localization.
    dependencies {
        exclude(dependency("net.kyori:adventure-api:.*"))
        exclude(dependency("net.kyori:adventure-key:.*"))
    }
    relocate("org.incendo.cloud", "com.trickshotmlg.devkit.examples.internal.cloud")
    relocate("io.leangen.geantyref", "com.trickshotmlg.devkit.examples.internal.geantyref")
    relocate("org.yaml.snakeyaml", "com.trickshotmlg.devkit.examples.internal.snakeyaml")
    relocate("net.kyori.adventure.text.minimessage", "com.trickshotmlg.devkit.examples.internal.minimessage")
    from(rootProject.file("../../THIRD-PARTY-NOTICES.md")) {
        into("META-INF")
        rename { "THIRD-PARTY-NOTICES.md" }
    }
    from(rootProject.file("../../THIRD-PARTY-LICENSES/LICENSE-APACHE-2.0.txt")) {
        into("META-INF/licenses")
    }
    from(rootProject.file("../../THIRD-PARTY-LICENSES/LICENSE-MIT-ADVENTURE.txt")) {
        into("META-INF/licenses")
    }
}
