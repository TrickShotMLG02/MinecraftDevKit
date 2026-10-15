plugins {
    id("java-gradle-plugin")
}

dependencies {
    implementation(project(":devkit-bukkit-metadata"))
    implementation(project(":devkit-localization"))
    implementation(project(":devkit-localization-yaml"))
    implementation("org.yaml:snakeyaml:2.7")
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("bukkitPermissions") {
            id = "com.trickshotmlg.devkit.bukkit-permissions"
            implementationClass = "com.trickshotmlg.devkit.gradle.bukkit.permissions.BukkitPermissionsPlugin"
            displayName = "MinecraftDevKit Bukkit Permission Generation"
            description = "Generates Bukkit plugin.yml permissions from a MinecraftDevKit PermissionCatalog."
        }
        create("localizationScan") {
            id = "com.trickshotmlg.devkit.localization-scan"
            implementationClass = "com.trickshotmlg.devkit.gradle.localization.LocalizationScanPlugin"
            displayName = "MinecraftDevKit Localization Scanner"
            description = "Scans translation resources against a declared text reference manifest."
        }
    }
}
