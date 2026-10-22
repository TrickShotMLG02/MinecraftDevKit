repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    api(project(":devkit-scheduler"))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testCompileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testRuntimeOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}
