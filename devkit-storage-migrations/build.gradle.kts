val integrationTestSourceSet = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}

configurations[integrationTestSourceSet.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[integrationTestSourceSet.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    api(project(":devkit-storage"))
    implementation("org.flywaydb:flyway-core:13.5.0")
    runtimeOnly("org.flywaydb:flyway-database-nc-sqlite:13.5.0")
    runtimeOnly("org.flywaydb:flyway-mysql:13.5.0")
    testImplementation("org.xerial:sqlite-jdbc:3.50.2.0")

    add("integrationTestImplementation", platform("org.testcontainers:testcontainers-bom:2.0.5"))
    add("integrationTestImplementation", "org.testcontainers:testcontainers-junit-jupiter")
    add("integrationTestImplementation", "org.testcontainers:testcontainers-mysql")
    add("integrationTestImplementation", "org.testcontainers:testcontainers-mariadb")
    add("integrationTestRuntimeOnly", "com.mysql:mysql-connector-j:26.10.0")
    add("integrationTestRuntimeOnly", "org.mariadb.jdbc:mariadb-java-client:3.5.7")
}

tasks.register<Test>("integrationTest") {
    description = "Runs optional MySQL and MariaDB Flyway integration tests (requires Docker)."
    group = "verification"
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    maxParallelForks = 1
    shouldRunAfter(tasks.named("test"))
}
