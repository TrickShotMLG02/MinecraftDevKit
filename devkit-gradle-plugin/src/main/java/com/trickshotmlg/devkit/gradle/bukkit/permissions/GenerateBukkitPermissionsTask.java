package com.trickshotmlg.devkit.gradle.bukkit.permissions;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;

/** Generates and merges the catalog's Bukkit permission declarations into plugin.yml. */
@CacheableTask
public abstract class GenerateBukkitPermissionsTask extends DefaultTask {
    private final WorkerExecutor workerExecutor;

    /** Creates the task with Gradle's worker executor.
     * @param workerExecutor Gradle worker executor
     */
    @Inject
    public GenerateBukkitPermissionsTask(WorkerExecutor workerExecutor) {
        this.workerExecutor = workerExecutor;
    }

    /** Compiled consumer classes and runtime dependencies used only in the isolated worker JVM.
     * @return generation classpath input
     */
    @Classpath
    public abstract ConfigurableFileCollection getCatalogClasspath();

    /** Fully qualified catalog declaration class.
     * @return catalog class input
     */
    @Input
    public abstract Property<String> getCatalogClass();

    /** Public static catalog field name.
     * @return catalog field input
     */
    @Input
    public abstract Property<String> getCatalogField();

    /** Whether the selected descriptor mode emits Bukkit plugin.yml metadata.
     * @return generation mode input
     */
    @Input
    public abstract Property<Boolean> getGenerationEnabled();

    /** The conventional source descriptor, which is allowed not to exist.
     * @return input descriptor files, normally {@code src/main/resources/plugin.yml}
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getBasePluginYml();

    /** Generated descriptor within the build directory.
     * @return generated plugin.yml output
     */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /** Executes isolated catalog loading, export, and descriptor merge. */
    @TaskAction
    public void generate() {
        WorkQueue queue = workerExecutor.processIsolation();
        queue.submit(CatalogGenerationWorkAction.class, parameters -> {
            parameters.getCatalogClasspath().from(getCatalogClasspath());
            parameters.getCatalogClass().set(getCatalogClass());
            parameters.getCatalogField().set(getCatalogField());
            parameters.getBasePluginYml().from(getBasePluginYml());
            parameters.getOutputFile().set(getOutputFile());
        });
        queue.await();
    }
}
