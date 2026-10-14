pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.trickshotmlg.devkit.bukkit-permissions") {
                // JitPack publishes repository modules under com.github.USER.REPO.
                // Resolve the implementation module directly instead of its plugin marker.
                useModule("com.github.TrickshotMLG.MinecraftDevKit:devkit-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
    }
}

rootProject.name = "minecraftdevkit-bukkit-permissions-example"
