package com.trickshotmlg.devkit.gradle.paper;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Generates a validated, deterministic native Paper plugin descriptor. */
@CacheableTask
public abstract class GeneratePaperPluginMetadataTask extends DefaultTask {
    private static final Pattern PLUGIN_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");
    private static final Pattern JAVA_CLASS = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+");
    private static final Pattern API_VERSION = Pattern.compile("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?");

    @Input public abstract Property<String> getPluginName();
    @Input public abstract Property<String> getVersion();
    @Input public abstract Property<String> getMainClass();
    @Input @Optional public abstract Property<String> getPluginDescription();
    @Input @Optional public abstract Property<String> getApiVersion();
    @Input @Optional public abstract Property<String> getBootstrapper();
    @Input @Optional public abstract Property<String> getLoader();
    @Input public abstract ListProperty<String> getAuthors();
    @Input public abstract ListProperty<PaperPluginDependencySpec> getBootstrapDependencies();
    @Input public abstract ListProperty<PaperPluginDependencySpec> getServerDependencies();
    @Input public abstract Property<Boolean> getGenerationEnabled();
    @OutputFile public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void generate() {
        try {
            String name = nonBlank(getPluginName().get(), "name");
            String version = nonBlank(getVersion().get(), "version");
            String main = javaClass(getMainClass().get(), "main");
            String description = optionalText(getPluginDescription().getOrNull(), "description");
            String apiVersion = optionalText(getApiVersion().getOrNull(), "apiVersion");
            if (apiVersion != null && !API_VERSION.matcher(apiVersion).matches()) {
                throw invalid("apiVersion must be a numeric Paper API version such as 1.21.11");
            }
            String bootstrapper = optionalClass(getBootstrapper().getOrNull(), "bootstrapper");
            String loader = optionalClass(getLoader().getOrNull(), "loader");
            List<String> authors = validateAuthors(getAuthors().get());

            LinkedHashMap<String, Object> descriptor = new LinkedHashMap<>();
            descriptor.put("name", name);
            descriptor.put("version", version);
            descriptor.put("main", main);
            if (description != null) descriptor.put("description", description);
            if (!authors.isEmpty()) descriptor.put("authors", authors);
            if (apiVersion != null) descriptor.put("api-version", apiVersion);
            if (bootstrapper != null) descriptor.put("bootstrapper", bootstrapper);
            if (loader != null) descriptor.put("loader", loader);
            Map<String, Object> dependencies = dependencies(name,
                    getBootstrapDependencies().get(), getServerDependencies().get());
            if (!dependencies.isEmpty()) descriptor.put("dependencies", dependencies);

            String text = yaml().dump(descriptor);
            // Round-trip once through SafeConstructor to guard serializer/config mistakes without
            // requiring Paper or any API classes during Gradle configuration.
            Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
            if (!(parsed instanceof Map<?, ?>)) throw invalid("generated descriptor root must be a YAML mapping");
            Path output = getOutputFile().get().getAsFile().toPath();
            Files.createDirectories(output.getParent());
            Files.writeString(output, text, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new GradleException("Could not write generated paper-plugin.yml", failure);
        }
    }

    private static Map<String, Object> dependencies(String self, List<PaperPluginDependencySpec> bootstrap,
                                                    List<PaperPluginDependencySpec> server) {
        Map<String, Object> result = new LinkedHashMap<>();
        addDependencies(result, self, PaperDependencyPhase.BOOTSTRAP, bootstrap);
        addDependencies(result, self, PaperDependencyPhase.SERVER, server);
        return result;
    }

    private static void addDependencies(Map<String, Object> result, String self, PaperDependencyPhase phase,
                                        List<PaperPluginDependencySpec> dependencies) {
        Map<String, Object> phaseEntries = new LinkedHashMap<>();
        Set<String> names = new LinkedHashSet<>();
        for (PaperPluginDependencySpec dependency : dependencies) {
            if (dependency == null || dependency.phase() != phase) throw invalid("dependency was assigned to the wrong Paper phase");
            String name = nonBlank(dependency.name(), "dependency name");
            if (!PLUGIN_NAME.matcher(name).matches()) throw invalid("invalid Paper dependency name '" + name + "'");
            if (name.equalsIgnoreCase(self)) throw invalid("plugin cannot depend on itself ('" + name + "')");
            String key = name.toLowerCase(Locale.ROOT);
            if (!names.add(key)) throw invalid("duplicate " + phase.name().toLowerCase(Locale.ROOT) + " dependency '" + name + "'");
            if (dependency.load() == null) throw invalid("dependency load order is required");

            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("load", dependency.load().name());
            definition.put("required", dependency.required());
            definition.put("join-classpath", dependency.joinClasspath());
            phaseEntries.put(name, definition);
        }
        if (!phaseEntries.isEmpty()) result.put(phase.name().toLowerCase(Locale.ROOT), phaseEntries);
    }

    private static List<String> validateAuthors(List<String> values) {
        List<String> authors = new ArrayList<>();
        for (String value : values) authors.add(nonBlank(value, "author"));
        return List.copyOf(authors);
    }

    private static String javaClass(String value, String field) {
        String result = nonBlank(value, field);
        if (!JAVA_CLASS.matcher(result).matches()) throw invalid(field + " must be a fully-qualified Java class name");
        return result;
    }

    private static String optionalClass(String value, String field) {
        return value == null || value.isBlank() ? null : javaClass(value, field);
    }

    private static String optionalText(String value, String field) {
        if (value == null) return null;
        return nonBlank(value, field);
    }

    private static String nonBlank(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field + " must not be blank");
        return value.trim();
    }

    private static GradleException invalid(String message) {
        return new GradleException("Invalid native Paper plugin metadata: " + message);
    }

    private static Yaml yaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        options.setPrettyFlow(false);
        options.setSplitLines(false);
        options.setAllowUnicode(true);
        options.setLineBreak(DumperOptions.LineBreak.UNIX);
        return new Yaml(options);
    }
}
