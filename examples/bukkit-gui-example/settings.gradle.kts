pluginManagement {
    val mode = providers.gradleProperty("mdkRepository").orElse("mavenLocal").get().lowercase()
    val requestedVersion = providers.gradleProperty("mdkVersion").orNull
    val version = when (mode) {
        "mavenlocal" -> {
            if (requestedVersion != null && requestedVersion != "0.1.0-SNAPSHOT") throw GradleException("mavenLocal mode uses 0.1.0-SNAPSHOT; remove -PmdkVersion")
            "0.1.0-SNAPSHOT"
        }
        "jitpack" -> requestedVersion?.takeIf { it.isNotBlank() && !it.endsWith("-SNAPSHOT") }
            ?: throw GradleException("jitpack mode requires -PmdkVersion with a published release tag")
        else -> throw GradleException("Unsupported -PmdkRepository='$mode'; use mavenLocal or jitpack")
    }
    repositories { gradlePluginPortal(); mavenCentral(); if (mode == "mavenlocal") mavenLocal() else maven("https://jitpack.io") }
    resolutionStrategy.eachPlugin {
        if (requested.id.id == "com.trickshotmlg.devkit.bukkit-permissions") {
            val group = if (mode == "mavenlocal") "com.trickshotmlg.devkit" else "com.github.TrickShotMLG02.MinecraftDevKit"
            useModule("$group:devkit-gradle-plugin:$version")
        }
    }
}
dependencyResolutionManagement {
    val mode = providers.gradleProperty("mdkRepository").orElse("mavenLocal").get().lowercase()
    repositories {
        mavenCentral()
        if (mode == "mavenlocal") mavenLocal() else maven("https://jitpack.io")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
rootProject.name = "minecraftdevkit-bukkit-gui-example"
