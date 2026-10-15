plugins { application }

dependencies { implementation(project(":devkit-localization")) }

application { mainClass.set("com.trickshotmlg.devkit.localization.cli.LocalizationScannerCli") }
