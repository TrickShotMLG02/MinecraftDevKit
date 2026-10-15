plugins { application }

dependencies {
    implementation(project(":devkit-localization"))
    implementation(project(":devkit-localization-yaml"))
}

application { mainClass.set("com.trickshotmlg.devkit.localization.cli.LocalizationScannerCli") }
