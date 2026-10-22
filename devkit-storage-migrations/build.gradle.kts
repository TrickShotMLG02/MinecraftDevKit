dependencies {
    api(project(":devkit-storage"))
    implementation("org.flywaydb:flyway-core:13.5.0")
    runtimeOnly("org.flywaydb:flyway-database-nc-sqlite:13.5.0")
    runtimeOnly("org.flywaydb:flyway-mysql:13.5.0")
    testImplementation("org.xerial:sqlite-jdbc:3.50.2.0")
}
