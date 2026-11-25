repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    mavenCentral()
}

dependencies {
    api(project(":devkit-bukkit-data"))
    api(project(":devkit-text"))
    api("net.kyori:adventure-api:4.26.1")
    implementation("net.kyori:adventure-text-serializer-legacy:4.26.1")
    implementation("net.kyori:adventure-text-minimessage:4.26.1")
    compileOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
    testCompileOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
    testRuntimeOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
}
