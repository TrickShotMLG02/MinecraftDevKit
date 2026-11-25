repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    api(project(":devkit-permissions"))
    compileOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
    testCompileOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
    testRuntimeOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
}
