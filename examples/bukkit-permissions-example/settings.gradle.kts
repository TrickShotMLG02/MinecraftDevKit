pluginManagement {
    val mdkRepository = providers.gradleProperty("mdkRepository").orElse("mavenLocal").get().lowercase()
    val requestedVersion = providers.gradleProperty("mdkVersion").orNull
    val mdkVersion = when (mdkRepository) {
        "mavenlocal" -> {
            if (requestedVersion != null && requestedVersion != "0.1.0-SNAPSHOT") {
                throw GradleException("mavenLocal mode uses MinecraftDevKit version 0.1.0-SNAPSHOT; remove -PmdkVersion")
            }
            "0.1.0-SNAPSHOT"
        }
        "jitpack" -> requestedVersion?.takeIf { it.isNotBlank() && !it.endsWith("-SNAPSHOT") }
            ?: throw GradleException("jitpack mode requires a release tag via -PmdkVersion, such as 0.1.0-beta.1")
        else -> throw GradleException("Unsupported -PmdkRepository='$mdkRepository'; use mavenLocal or jitpack")
    }

    repositories {
        mavenCentral()
        if (mdkRepository == "mavenlocal") mavenLocal()
        else maven("https://jitpack.io")
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.trickshotmlg.devkit.bukkit-permissions") {
                val module = if (mdkRepository == "mavenlocal") {
                    "com.trickshotmlg.devkit:devkit-gradle-plugin:$mdkVersion"
                } else {
                    "com.github.TrickShotMLG02.MinecraftDevKit:devkit-gradle-plugin:$mdkVersion"
                }
                useModule(module)
            }
        }
    }
}

dependencyResolutionManagement {
    val mdkRepository = providers.gradleProperty("mdkRepository").orElse("mavenLocal").get().lowercase()
    repositories {
        mavenCentral()
        if (mdkRepository == "mavenlocal") mavenLocal()
        else maven("https://jitpack.io")
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
    }
}

rootProject.name = "minecraftdevkit-bukkit-permissions-example"
