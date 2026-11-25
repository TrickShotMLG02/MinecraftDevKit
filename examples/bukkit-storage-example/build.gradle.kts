plugins {
    java
    id("com.trickshotmlg.devkit.bukkit-permissions") version "0.1.0-SNAPSHOT"
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
val mdkGroup = if (mode == "mavenlocal") "com.trickshotmlg.devkit"
    else "com.github.TrickShotMLG02.MinecraftDevKit"
val serverPlatform = providers.gradleProperty("mdkPlatform").orElse("paper").get().lowercase()
if (serverPlatform !in setOf("paper", "spigot")) {
    throw GradleException("Unsupported -PmdkPlatform='$serverPlatform'; use paper or spigot")
}

dependencies {
    if (serverPlatform == "paper") {
        compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    } else {
        compileOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
    }
    implementation("$mdkGroup:devkit-bukkit-lifecycle:$mdkVersion")
    implementation("$mdkGroup:devkit-bukkit-scheduler:$mdkVersion")
    implementation("$mdkGroup:devkit-bukkit-commands:$mdkVersion")
    implementation("$mdkGroup:devkit-config-yaml:$mdkVersion")
    implementation("$mdkGroup:devkit-storage:$mdkVersion")
    implementation("$mdkGroup:devkit-storage-migrations:$mdkVersion")
    implementation("$mdkGroup:devkit-permissions:$mdkVersion")
    implementation("net.kyori:adventure-text-serializer-legacy:4.26.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

minecraftDevKit {
    bukkitPermissions {
        catalogClass.set("com.trickshotmlg.devkit.examples.storage.StoragePermissions")
        catalogField.set("CATALOG")
    }
}

tasks.test {
    useJUnitPlatform()
    inputs.property("mdkPlatform", serverPlatform)
    dependsOn(tasks.shadowJar)
    doFirst {
        systemProperty("mdk.plugin.jar", tasks.shadowJar.get().archiveFile.get().asFile.absolutePath)
    }
}
tasks.jar { enabled = false }
tasks.shadowJar {
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()
    if (serverPlatform == "paper") {
        dependencies {
            exclude(dependency("net.kyori:adventure-api:.*"))
            exclude(dependency("net.kyori:adventure-key:.*"))
        }
    }
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    relocate("org.incendo.cloud", "com.trickshotmlg.devkit.examples.internal.cloud")
    relocate("io.leangen.geantyref", "com.trickshotmlg.devkit.examples.internal.geantyref")
    relocate("org.yaml.snakeyaml", "com.trickshotmlg.devkit.examples.internal.snakeyaml")
    relocate("org.flywaydb", "com.trickshotmlg.devkit.examples.internal.flyway")
    relocate("com.zaxxer.hikari", "com.trickshotmlg.devkit.examples.internal.hikari")
    from(rootProject.file("../../THIRD-PARTY-NOTICES.md")) { into("META-INF"); rename { "THIRD-PARTY-NOTICES.md" } }
    from(rootProject.file("../../THIRD-PARTY-LICENSES/LICENSE-APACHE-2.0.txt")) { into("META-INF/licenses") }
}
