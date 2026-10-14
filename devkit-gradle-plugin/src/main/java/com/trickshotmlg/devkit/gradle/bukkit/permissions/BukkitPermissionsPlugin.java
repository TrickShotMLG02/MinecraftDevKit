package com.trickshotmlg.devkit.gradle.bukkit.permissions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.language.jvm.tasks.ProcessResources;

/** Adds compile-time Bukkit permission metadata generation to a Java project. */
public final class BukkitPermissionsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        MinecraftDevKitExtension extension = project.getExtensions().create(
                "minecraftDevKit", MinecraftDevKitExtension.class);
        BukkitPermissionsExtension permissions = extension.getBukkitPermissions();
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        Configuration runtimeDependencies = project.getConfigurations()
                .getByName(main.getRuntimeClasspathConfigurationName());
        java.io.File baseDescriptor = project.getLayout().getProjectDirectory()
                .file("src/main/resources/plugin.yml").getAsFile();

        TaskProvider<GenerateBukkitPermissionsTask> generate = project.getTasks().register(
                "generateBukkitPermissions", GenerateBukkitPermissionsTask.class, task -> {
                    task.setGroup("minecraft dev kit");
                    task.setDescription("Generates and merges Bukkit plugin.yml permissions from a Java PermissionCatalog.");
                    task.getCatalogClass().set(permissions.getCatalogClass());
                    task.getCatalogField().set(permissions.getCatalogField());
                    task.getBasePluginYml().from(baseDescriptor);
                    task.getCatalogClasspath().from(main.getOutput().getClassesDirs(), runtimeDependencies);
                    task.getOutputFile().set(project.getLayout().getBuildDirectory()
                            .file("generated/minecraftdevkit/resources/plugin.yml"));
                    task.dependsOn(project.getTasks().named(main.getCompileJavaTaskName()));
                });

        project.getTasks().named(main.getProcessResourcesTaskName(), ProcessResources.class).configure(task -> {
            task.dependsOn(generate);
            // The root source descriptor is merged by the generator and must not also be copied
            // unchanged, which would create duplicate plugin.yml entries in the final JAR.
            task.exclude(details -> baseDescriptor.equals(details.getFile()));
            task.from(generate.flatMap(GenerateBukkitPermissionsTask::getOutputFile));
        });
    }
}
