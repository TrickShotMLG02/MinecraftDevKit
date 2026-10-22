repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    api(project(":devkit-text"))
    api(project(":devkit-permissions"))
    api("net.kyori:adventure-api:4.26.1")
    implementation("net.kyori:adventure-text-minimessage:4.26.1")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testCompileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testRuntimeOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}
