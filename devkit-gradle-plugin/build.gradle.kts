plugins {
    id("java-gradle-plugin")
}

dependencies {
    implementation(project(":devkit-bukkit-metadata"))
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
    }
}
