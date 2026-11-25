package com.trickshotmlg.devkit.gradle.bukkit.permissions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.language.jvm.tasks.ProcessResources;
import com.trickshotmlg.devkit.gradle.paper.GeneratePaperPluginMetadataTask;
import com.trickshotmlg.devkit.gradle.paper.PluginDescriptorPlatform;
import com.trickshotmlg.devkit.gradle.paper.PaperPluginMetadataExtension;

/** Adds compile-time Bukkit permission metadata generation to a Java project. */
public final class BukkitPermissionsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        MinecraftDevKitExtension extension = project.getExtensions().create(
                "minecraftDevKit", MinecraftDevKitExtension.class);
        BukkitPermissionsExtension permissions = extension.getBukkitPermissions();
        PaperPluginMetadataExtension paper = extension.getPaperPlugin();
        paper.getName().convention(project.getName());
        paper.getVersion().convention(project.provider(() -> project.getVersion().toString()));
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
                    task.getGenerationEnabled().set(extension.getPlatform().map(platform -> platform == PluginDescriptorPlatform.BUKKIT));
                    task.onlyIf(ignored -> task.getGenerationEnabled().get());
                    task.dependsOn(project.getTasks().named(main.getCompileJavaTaskName()));
                });

        TaskProvider<GeneratePaperPluginMetadataTask> paperMetadata = project.getTasks().register(
                "generatePaperPluginMetadata", GeneratePaperPluginMetadataTask.class, task -> {
                    task.setGroup("minecraft dev kit");
                    task.setDescription("Generates a native Paper paper-plugin.yml descriptor.");
                    task.getPluginName().set(paper.getName());
                    task.getVersion().set(paper.getVersion());
                    task.getMainClass().set(paper.getMainClass());
                    task.getPluginDescription().set(paper.getDescription());
                    task.getApiVersion().set(paper.getApiVersion());
                    task.getBootstrapper().set(paper.getBootstrapper());
                    task.getLoader().set(paper.getLoader());
                    task.getAuthors().set(paper.getAuthors());
                    task.getBootstrapDependencies().set(paper.getBootstrapDependencySpecs());
                    task.getServerDependencies().set(paper.getServerDependencySpecs());
                    task.getGenerationEnabled().set(extension.getPlatform().map(platform -> platform == PluginDescriptorPlatform.PAPER_NATIVE));
                    task.onlyIf(ignored -> task.getGenerationEnabled().get());
                    task.getOutputFile().set(project.getLayout().getBuildDirectory()
                            .file("generated/minecraftdevkit/resources/paper-plugin.yml"));
                });

        project.getTasks().named(main.getProcessResourcesTaskName(), ProcessResources.class).configure(task -> {
            task.getInputs().property("minecraftDevKit.platform", extension.getPlatform());
            task.dependsOn(generate, paperMetadata);
            java.io.File basePaperDescriptor = project.getLayout().getProjectDirectory()
                    .file("src/main/resources/paper-plugin.yml").getAsFile();
            java.io.File generatedBukkitDescriptor = project.getLayout().getBuildDirectory()
                    .file("generated/minecraftdevkit/resources/plugin.yml").get().getAsFile();
            java.io.File generatedPaperDescriptor = project.getLayout().getBuildDirectory()
                    .file("generated/minecraftdevkit/resources/paper-plugin.yml").get().getAsFile();
            // Source descriptors are deliberately replaced by exactly one generated descriptor.
            task.exclude(details -> baseDescriptor.equals(details.getFile())
                    || basePaperDescriptor.equals(details.getFile()));
            task.exclude(details -> extension.getPlatform().get() == PluginDescriptorPlatform.PAPER_NATIVE
                    ? generatedBukkitDescriptor.equals(details.getFile())
                    : generatedPaperDescriptor.equals(details.getFile()));
            task.from(generate.flatMap(GenerateBukkitPermissionsTask::getOutputFile));
            task.from(paperMetadata.flatMap(GeneratePaperPluginMetadataTask::getOutputFile));
        });
    }
}
