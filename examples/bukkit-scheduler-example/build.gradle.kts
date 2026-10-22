plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.trickshotmlg.devkit.examples"
version = "0.1.0-SNAPSHOT"
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

val mode = providers.gradleProperty("mdkRepository").orElse("mavenLocal").get().lowercase()
val mdkVersion = if (mode == "mavenLocal" || mode == "mavenlocal") "0.1.0-SNAPSHOT"
    else providers.gradleProperty("mdkVersion").get()
val mdkGroup = if (mode == "mavenLocal" || mode == "mavenlocal") "com.trickshotmlg.devkit"
    else "com.github.TrickShotMLG02.MinecraftDevKit"

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("$mdkGroup:devkit-bukkit-scheduler:$mdkVersion")
    implementation("$mdkGroup:devkit-bukkit-commands:$mdkVersion")
}

tasks.jar { enabled = false }
tasks.shadowJar {
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    relocate("org.incendo.cloud", "com.trickshotmlg.devkit.examples.internal.cloud")
    relocate("io.leangen.geantyref", "com.trickshotmlg.devkit.examples.internal.geantyref")
    from(rootProject.file("../../THIRD-PARTY-NOTICES.md")) { into("META-INF"); rename { "THIRD-PARTY-NOTICES.md" } }
    from(rootProject.file("../../THIRD-PARTY-LICENSES/LICENSE-APACHE-2.0.txt")) { into("META-INF/licenses") }
    from(rootProject.file("../../THIRD-PARTY-LICENSES/LICENSE-MIT-ADVENTURE.txt")) { into("META-INF/licenses") }
}
