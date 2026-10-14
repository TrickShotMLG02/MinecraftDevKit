package com.trickshotmlg.devkit.gradle.bukkit.permissions;

import com.trickshotmlg.devkit.bukkit.metadata.BukkitPermissionYamlExporter;
import com.trickshotmlg.devkit.permissions.PermissionCatalog;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Runs catalog initialization and YAML serialization in a process-isolated Gradle worker. */
public abstract class CatalogGenerationWorkAction implements WorkAction<CatalogGenerationWorkAction.Parameters> {
    /** Inputs transferred to the process-isolated catalog worker. */
    public interface Parameters extends WorkParameters {
        /** @return consumer compiled classpath */
        ConfigurableFileCollection getCatalogClasspath();

        /** @return catalog class name */
        Property<String> getCatalogClass();

        /** @return catalog field name */
        Property<String> getCatalogField();

        /** @return optional source plugin descriptor */
        ConfigurableFileCollection getBasePluginYml();

        /** @return generated descriptor output */
        RegularFileProperty getOutputFile();
    }

    @Override
    public void execute() {
        String className = getParameters().getCatalogClass().get();
        String fieldName = getParameters().getCatalogField().get();

        try (URLClassLoader catalogLoader = catalogClassLoader()) {
            PermissionCatalog catalog = loadCatalog(catalogLoader, className, fieldName);
            String generatedPermissions = BukkitPermissionYamlExporter.export(catalog);
            Path basePluginYml = getParameters().getBasePluginYml().getFiles().stream()
                    .filter(java.io.File::isFile)
                    .map(java.io.File::toPath)
                    .findFirst()
                    .orElse(null);
            String merged = mergePluginYaml(basePluginYml, generatedPermissions);
            writeOutput(getParameters().getOutputFile().get().getAsFile().toPath(), merged);
        } catch (GradleException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new GradleException("Could not read or write generated Bukkit plugin.yml metadata", failure);
        } catch (LinkageError failure) {
            throw new GradleException("Could not initialize permission catalog " + className + "."
                    + fieldName + ". Check that its compiled classes and runtime dependencies are available.", failure);
        }
    }

    private URLClassLoader catalogClassLoader() throws IOException {
        Set<URL> urls = new LinkedHashSet<>();
        for (java.io.File file : getParameters().getCatalogClasspath().getFiles()) {
            urls.add(file.toURI().toURL());
        }
        // The parent lives in the isolated worker JVM and contains the permission API/exporter.
        // It is never the Gradle daemon's classloader.
        return new URLClassLoader(urls.toArray(URL[]::new),
                BukkitPermissionYamlExporter.class.getClassLoader());
    }

    private static PermissionCatalog loadCatalog(ClassLoader loader, String className, String fieldName) {
        final Class<?> catalogClass;
        try {
            catalogClass = Class.forName(className, true, loader);
        } catch (ClassNotFoundException failure) {
            throw new GradleException("Permission catalog class '" + className
                    + "' was not found on the main source set's compiled runtime classpath.", failure);
        }

        final Field field;
        try {
            field = catalogClass.getField(fieldName);
        } catch (NoSuchFieldException failure) {
            throw new GradleException("Permission catalog class '" + className
                    + "' has no public field named '" + fieldName + "'.", failure);
        }
        if (!Modifier.isStatic(field.getModifiers())) {
            throw new GradleException("Permission catalog field '" + className + "." + fieldName
                    + "' must be static.");
        }
        if (!PermissionCatalog.class.isAssignableFrom(field.getType())) {
            throw new GradleException("Permission catalog field '" + className + "." + fieldName
                    + "' must have type PermissionCatalog, but has type " + field.getType().getName() + ".");
        }

        try {
            Object value = field.get(null);
            if (!(value instanceof PermissionCatalog catalog)) {
                throw new GradleException("Permission catalog field '" + className + "." + fieldName
                        + "' evaluated to null or did not contain a PermissionCatalog.");
            }
            return catalog;
        } catch (IllegalAccessException failure) {
            throw new GradleException("Permission catalog field '" + className + "." + fieldName
                    + "' is not accessible. Declare it public static.", failure);
        }
    }

    private static String mergePluginYaml(Path baseFile, String generatedPermissions) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        if (baseFile != null && Files.exists(baseFile)) {
            Object document;
            try {
                document = safeLoad(Files.readString(baseFile, StandardCharsets.UTF_8));
            } catch (RuntimeException failure) {
                throw new GradleException("Could not parse base plugin descriptor '" + baseFile
                        + "' as safe YAML: " + failure.getMessage(), failure);
            }
            if (!(document instanceof Map<?, ?> sourceRoot)) {
                throw new GradleException("Base plugin descriptor '" + baseFile
                        + "' must contain a YAML mapping at the document root.");
            }
            root.putAll(stringKeyedMap(sourceRoot, "top-level plugin.yml"));
        }

        Map<String, Object> generatedRoot = stringKeyedMap(
                (Map<?, ?>) safeLoad(generatedPermissions), "generated plugin.yml");
        Map<String, Object> generated = stringKeyedMap(
                (Map<?, ?>) generatedRoot.get("permissions"), "generated permissions");

        Object existingValue = root.get("permissions");
        Map<String, Object> permissions = new LinkedHashMap<>();
        if (existingValue != null) {
            if (!(existingValue instanceof Map<?, ?> existingMap)) {
                throw new GradleException("The top-level 'permissions' entry in plugin.yml must be a mapping.");
            }
            permissions.putAll(stringKeyedMap(existingMap, "permissions"));
        } else if (root.containsKey("permissions")) {
            throw new GradleException("The top-level 'permissions' entry in plugin.yml must be a mapping, not null.");
        }
        validatePermissionDefinitions(permissions, "Existing");
        validatePermissionDefinitions(generated, "Generated");

        for (Map.Entry<String, Object> generatedEntry : generated.entrySet()) {
            if (permissions.containsKey(generatedEntry.getKey())) {
                throw new GradleException("Permission node '" + generatedEntry.getKey()
                        + "' is declared both in the source plugin.yml and the Java catalog. Remove one definition.");
            }
            permissions.put(generatedEntry.getKey(), generatedEntry.getValue());
        }
        root.put("permissions", permissions);
        return yaml().dump(root);
    }

    private static Map<String, Object> stringKeyedMap(Map<?, ?> source, String description) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new GradleException("Every key in " + description + " must be a string.");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static void validatePermissionDefinitions(Map<String, Object> permissions, String source) {
        for (Map.Entry<String, Object> entry : permissions.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> definition)) {
                throw new GradleException(source + " permission definition '" + entry.getKey()
                        + "' must be a YAML mapping.");
            }
            stringKeyedMap(definition, source + " permission definition '" + entry.getKey() + "'");
        }
    }

    private static Yaml yaml() {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setIndent(2);
        dumperOptions.setIndicatorIndent(0);
        dumperOptions.setPrettyFlow(false);
        dumperOptions.setSplitLines(false);
        dumperOptions.setAllowUnicode(true);
        dumperOptions.setLineBreak(DumperOptions.LineBreak.UNIX);

        return new Yaml(dumperOptions);
    }

    private static Object safeLoad(String text) {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(loaderOptions)).load(text);
    }

    private static void writeOutput(Path output, String contents) throws IOException {
        Objects.requireNonNull(output, "output");
        Files.createDirectories(output.getParent());
        Files.writeString(output, contents, StandardCharsets.UTF_8);
    }
}
