plugins {
    java
    id("com.trickshotmlg.devkit.bukkit-permissions") version "0.1.0-SNAPSHOT"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.trickshotmlg.devkit.examples"
version = "0.1.0-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    implementation("com.trickshotmlg.devkit:devkit-permissions:0.1.0-SNAPSHOT")
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
