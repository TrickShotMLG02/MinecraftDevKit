plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.trickshotmlg.devkit.tests"
version = "0.1.0-SNAPSHOT"
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

val mdkVersion = providers.gradleProperty("mdkVersion").orElse("0.1.0-SNAPSHOT").get()
val platform = providers.gradleProperty("mdkPlatform").orElse("paper").get().lowercase()
require(platform == "paper" || platform == "spigot") { "-PmdkPlatform must be paper or spigot" }

dependencies {
    if (platform == "paper") compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    else compileOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")

    implementation("com.trickshotmlg.devkit:devkit-bukkit-commands:$mdkVersion")
    implementation("com.trickshotmlg.devkit:devkit-bukkit-gui:$mdkVersion")
    implementation("com.trickshotmlg.devkit:devkit-bukkit-lifecycle:$mdkVersion")
    implementation("com.trickshotmlg.devkit:devkit-bukkit-scheduler:$mdkVersion")
    implementation("com.trickshotmlg.devkit:devkit-config-yaml:$mdkVersion")
    implementation("com.trickshotmlg.devkit:devkit-localization-yaml:$mdkVersion")
    implementation("com.trickshotmlg.devkit:devkit-storage:$mdkVersion")
    implementation("com.trickshotmlg.devkit:devkit-storage-migrations:$mdkVersion")
    if (platform == "paper") implementation("com.trickshotmlg.devkit:devkit-paper-gui:$mdkVersion")
    implementation("net.kyori:adventure-text-serializer-legacy:4.26.1")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }
tasks.jar { enabled = false }
tasks.shadowJar {
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()
    filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
    if (platform == "paper") dependencies {
        exclude(dependency("net.kyori:adventure-api:.*"))
        exclude(dependency("net.kyori:adventure-key:.*"))
    }
    relocate("org.incendo.cloud", "com.trickshotmlg.devkit.runtime.internal.cloud")
    relocate("io.leangen.geantyref", "com.trickshotmlg.devkit.runtime.internal.geantyref")
    relocate("org.yaml.snakeyaml", "com.trickshotmlg.devkit.runtime.internal.snakeyaml")
    relocate("org.flywaydb", "com.trickshotmlg.devkit.runtime.internal.flyway")
    relocate("com.zaxxer.hikari", "com.trickshotmlg.devkit.runtime.internal.hikari")
    from(file("../../../THIRD-PARTY-NOTICES.md")) { into("META-INF"); rename { "THIRD-PARTY-NOTICES.md" } }
    from(file("../../../THIRD-PARTY-LICENSES/LICENSE-APACHE-2.0.txt")) { into("META-INF/licenses") }
}
