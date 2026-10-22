import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

plugins {
    base
}

// JitPack exposes the owner group and repository artifact separately; multi-module coordinates use both.
val jitpackGroup = providers.environmentVariable("GROUP")
    .zip(providers.environmentVariable("ARTIFACT")) { ownerGroup, repositoryArtifact -> "$ownerGroup.$repositoryArtifact" }
val configuredGroup = jitpackGroup
    .orElse(providers.environmentVariable("GROUP"))
    .orElse("com.trickshotmlg.devkit")
val configuredVersion = providers.environmentVariable("VERSION").orElse(providers.gradleProperty("projectVersion").orElse("0.1.0-SNAPSHOT"))

allprojects {
    group = configuredGroup.get()
    version = configuredVersion.get()
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    val isBukkitAdapter = name == "devkit-bukkit" || name == "devkit-bukkit-commands" || name == "devkit-bukkit-gui" || name == "devkit-bukkit-scheduler" || name == "devkit-bukkit-lifecycle"
    val targetJava = if (isBukkitAdapter) 21 else 17

    extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJava))
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(targetJava)
    }

    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        // Package documentation alone is not a Javadoc API. Skip until a module has types.
        onlyIf("Module has Java types to document") {
            source.files.any { it.extension == "java" && it.name != "package-info.java" }
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("file.encoding", "UTF-8")
    }

    tasks.withType<Jar>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.12.2"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    extensions.configure<org.gradle.api.publish.PublishingExtension> {
        publications {
            withType<MavenPublication>().configureEach {
                pom {
                    name.set(project.name)
                    description.set("${project.name} module of MinecraftDevKit")
                    url.set("https://github.com/TrickShotMLG02/MinecraftDevKit")
                    licenses {
                        license {
                            name.set("GNU General Public License v3.0 only (GPL-3.0-only)")
                            url.set("https://www.gnu.org/licenses/gpl-3.0.html")
                            distribution.set("repo")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/TrickShotMLG02/MinecraftDevKit.git")
                        developerConnection.set("scm:git:ssh://github.com/TrickShotMLG02/MinecraftDevKit.git")
                        url.set("https://github.com/TrickShotMLG02/MinecraftDevKit")
                    }
                }
            }
            // java-gradle-plugin creates pluginMaven and plugin marker publications itself.
            // Creating another Java component publication for that project targets the same
            // implementation coordinates and can cause conflicting publication tasks.
            if (project.name != "devkit-gradle-plugin") {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    artifactId = project.name
                }
            }
        }
    }
}

tasks.register("publishToMavenLocal") {
    group = "publishing"
    description = "Publishes all library modules to the local Maven repository."
    dependsOn(subprojects.map { "${it.path}:publishToMavenLocal" })
}
