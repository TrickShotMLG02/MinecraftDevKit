package com.trickshotmlg.devkit.config.yaml;

import com.trickshotmlg.devkit.config.ConfigIssue;
import com.trickshotmlg.devkit.config.ConfigKey;
import com.trickshotmlg.devkit.config.ConfigSchema;
import com.trickshotmlg.devkit.config.ConfigStore;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Safe YAML loading and default document generation for typed configuration schemas. */
public final class YamlConfigFile {
    public static final long DEFAULT_MAX_BYTES = 1_048_576;
    private final ConfigSchema schema;
    private final UnknownKeyPolicy unknownKeyPolicy;
    private final long maxBytes;
    private final ConfigMigrationRegistry migrations;

    public YamlConfigFile(ConfigSchema schema) { this(schema, UnknownKeyPolicy.PRESERVE_AND_WARN, DEFAULT_MAX_BYTES); }
    public YamlConfigFile(ConfigSchema schema, UnknownKeyPolicy unknownKeyPolicy) {
        this(schema, unknownKeyPolicy, DEFAULT_MAX_BYTES, ConfigMigrationRegistry.builder().build());
    }
    public YamlConfigFile(ConfigSchema schema, UnknownKeyPolicy unknownKeyPolicy, long maxBytes) {
        this(schema, unknownKeyPolicy, maxBytes, ConfigMigrationRegistry.builder().build());
    }
    public YamlConfigFile(ConfigSchema schema, UnknownKeyPolicy unknownKeyPolicy,
                          ConfigMigrationRegistry migrations) {
        this(schema, unknownKeyPolicy, DEFAULT_MAX_BYTES, migrations);
    }
    public YamlConfigFile(ConfigSchema schema, UnknownKeyPolicy unknownKeyPolicy, long maxBytes,
                          ConfigMigrationRegistry migrations) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.unknownKeyPolicy = Objects.requireNonNull(unknownKeyPolicy, "unknownKeyPolicy");
        this.migrations = Objects.requireNonNull(migrations, "migrations");
        if (maxBytes < 1 || maxBytes >= Integer.MAX_VALUE) throw new IllegalArgumentException("maxBytes must be between 1 and Integer.MAX_VALUE");
        this.maxBytes = maxBytes;
    }

    /** Loads one complete YAML document, applies defaults, and rejects invalid known values. */
    public YamlConfigLoadResult load(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        byte[] bytes;
        try (InputStream input = Files.newInputStream(file)) { bytes = input.readNBytes(Math.toIntExact(Math.min(maxBytes + 1, Integer.MAX_VALUE))); }
        if (bytes.length > maxBytes) throw new IOException("Configuration exceeds " + maxBytes + " bytes: " + file);
        Map<String, Object> flattened = parse(bytes, file.toString(), maxBytes, schema);
        LinkedHashMap<String, Object> candidate = new LinkedHashMap<>(flattened);
        int sourceVersion = readVersion(candidate.remove("config-version"), file);
        if (sourceVersion > schema.version()) throw new YamlConfigException("Configuration version " + sourceVersion
                + " is newer than supported schema version " + schema.version() + " in " + file, List.of());
        int migrationVersion = sourceVersion;
        while (migrationVersion < schema.version()) {
            ConfigMigration migration = migrations.migrationFrom(migrationVersion);
            if (migration == null) throw new YamlConfigException("No configuration migration from version "
                    + migrationVersion + " to " + (migrationVersion + 1) + " in " + file, List.of());
            try { migration.migrate(new MutableConfigValues(candidate)); }
            catch (Exception exception) {
                // Migration code can inspect arbitrary raw values, including credentials. Do not
                // propagate its message or cause into diagnostics where it could disclose them.
                throw new YamlConfigException("Configuration migration " + migrationVersion
                        + " -> " + (migrationVersion + 1) + " failed in " + file
                        + " (" + exception.getClass().getSimpleName() + ")", List.of());
            }
            migrationVersion++;
        }
        LinkedHashMap<String, Object> known = new LinkedHashMap<>();
        TreeMap<String, Object> unknown = new TreeMap<>();
        ArrayList<ConfigIssue> issues = new ArrayList<>();
        candidate.forEach((path, value) -> {
            if (schema.key(path).isPresent()) known.put(path, value);
            else {
                unknown.put(path, freeze(value));
                if (unknownKeyPolicy == UnknownKeyPolicy.PRESERVE_AND_WARN) {
                    issues.add(new ConfigIssue(path, ConfigIssue.Severity.WARNING, "unknown configuration key preserved",
                            java.util.Optional.of(file.toString()), java.util.Optional.empty()));
                } else if (unknownKeyPolicy == UnknownKeyPolicy.REJECT) {
                    issues.add(new ConfigIssue(path, ConfigIssue.Severity.ERROR, "unknown configuration key",
                            java.util.Optional.of(file.toString()), java.util.Optional.empty()));
                }
            }
        });
        if (issues.stream().anyMatch(issue -> issue.severity() == ConfigIssue.Severity.ERROR)) {
            throw new YamlConfigException("Unknown configuration values in " + file, issues);
        }
        try {
            return new YamlConfigLoadResult(ConfigStore.fromValues(schema, known), Collections.unmodifiableMap(unknown), issues,
                    sourceVersion < schema.version(), sourceVersion, java.util.Optional.of(fingerprint(bytes)));
        } catch (RuntimeException exception) {
            List<ConfigIssue> validationIssues = exception instanceof com.trickshotmlg.devkit.config.ConfigValidationException validation
                    ? validation.issues().stream().map(issue -> new ConfigIssue(issue.path(), issue.severity(), issue.message(),
                    java.util.Optional.of(file.toString()), issue.expected())).toList() : List.of();
            throw new YamlConfigException("Configuration validation failed in " + file + ": " + safeMessage(exception), exception,
                    validationIssues);
        }
    }

    /** Writes schema defaults only if the target does not already exist. */
    public boolean createDefaults(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        byte[] bytes = defaultsYaml().getBytes(StandardCharsets.UTF_8);
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try {
            Files.write(file, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return true;
        } catch (java.nio.file.FileAlreadyExistsException exists) { return false; }
    }

    /** Deterministically serializes only schema keys that have defaults. */
    public String defaultsYaml() {
        ConfigStore.defaults(schema); // fail before emitting a document missing required values or violating cross-field constraints
        TreeMap<String, Object> flattened = new TreeMap<>();
        flattened.put("config-version", schema.version());
        for (ConfigKey<?> key : schema.keys().values()) key.defaultValue().ifPresent(value -> flattened.put(key.path(), encode(key, value)));
        String body = dump(nest(flattened));
        StringBuilder comments = new StringBuilder();
        for (ConfigKey<?> key : schema.keys().values()) key.description().ifPresent(description -> {
            String normalized = description.replace('\r', ' ').replace('\n', ' ');
            comments.append("# ").append(key.path()).append(": ").append(normalized).append('\n');
        });
        return comments.append(body).toString();
    }

    int schemaVersion() { return schema.version(); }

    /** Serializes a store and preserved unknown values; persistence is added by the file handle API. */
    public String serialize(ConfigStore store, Map<String, ?> unknownValues) {
        if (store.schema() != schema) throw new IllegalArgumentException("Store belongs to a different schema");
        return serialize(store.snapshot(), unknownValues);
    }

    /** Serializes one already coherent immutable snapshot. */
    public String serialize(com.trickshotmlg.devkit.config.ConfigSnapshot snapshot, Map<String, ?> unknownValues) {
        if (snapshot.schema() != schema) throw new IllegalArgumentException("Snapshot belongs to a different schema");
        TreeMap<String, Object> flat = new TreeMap<>();
        flat.put("config-version", schema.version());
        snapshot.values().forEach((path, value) -> flat.put(path, encode(schema.key(path).orElseThrow(), value)));
        unknownValues.forEach((path, value) -> {
            if (schema.key(path).isPresent()) throw new IllegalArgumentException("Unknown-value map collides with schema key '" + path + "'");
            if (flat.putIfAbsent(path, freeze(value)) != null) throw new IllegalArgumentException("Duplicate configuration path '" + path + "'");
        });
        return dump(nest(flat));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object encode(ConfigKey key, Object value) {
        try { return key.codec().encode(value); }
        catch (RuntimeException exception) {
            if (key.sensitive()) throw new IllegalArgumentException("Unable to encode sensitive configuration value at '" + key.path() + "'");
            throw exception;
        }
    }

    private String dump(Map<String, Object> root) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        options.setAllowUnicode(true);
        options.setLineBreak(DumperOptions.LineBreak.UNIX);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        StringWriter output = new StringWriter();
        new Yaml(options).dump(root, output);
        return output.toString();
    }

    private static Map<String, Object> parse(byte[] bytes, String source, long maxBytes, ConfigSchema schema) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(20);
        options.setNestingDepthLimit(32);
        options.setCodePointLimit((int) maxBytes);
        try {
            Object root = new Yaml(new SafeConstructor(options)).load(new InputStreamReader(
                    new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
            if (root == null) return Map.of();
            if (!(root instanceof Map<?, ?> map)) throw new IOException("Configuration YAML root must be a mapping: " + source);
            TreeMap<String, Object> flattened = new TreeMap<>();
            flatten(map, "", flattened, source, new IdentityHashMap<>(), 0, schema);
            return Collections.unmodifiableMap(flattened);
        } catch (IOException exception) { throw exception; }
        catch (RuntimeException exception) { throw new IOException("Invalid configuration YAML in " + source + ": " + exception.getMessage(), exception); }
    }

    private static void flatten(Map<?, ?> input, String prefix, Map<String, Object> output,
                                String source, IdentityHashMap<Object, Boolean> ancestors, int depth,
                                ConfigSchema schema) throws IOException {
        if (depth > 32) throw new IOException("Configuration nesting exceeds 32 levels in " + source);
        if (ancestors.put(input, Boolean.TRUE) != null) throw new IOException("Recursive YAML aliases are not supported in " + source);
        try {
            for (Map.Entry<?, ?> entry : input.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.isBlank() || key.contains(".")) {
                    throw new IOException("Configuration keys must be non-empty strings without dots in " + source + ": " + entry.getKey());
                }
                String path = prefix.isEmpty() ? key : prefix + "." + key;
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> child && schema.key(path).isEmpty())
                    flatten(child, path, output, source, ancestors, depth + 1, schema);
                else {
                    ensureAcyclic(value, ancestors, source, depth + 1);
                    if (output.putIfAbsent(path, freeze(value)) != null) throw new IOException("Duplicate flattened configuration key '" + path + "' in " + source);
                }
            }
        } finally { ancestors.remove(input); }
    }

    private static void ensureAcyclic(Object value, IdentityHashMap<Object, Boolean> ancestors, String source, int depth) throws IOException {
        if (depth > 32) throw new IOException("Configuration nesting exceeds 32 levels in " + source);
        if (value instanceof Map<?, ?> map) {
            if (ancestors.put(map, Boolean.TRUE) != null) throw new IOException("Recursive YAML aliases are not supported in " + source);
            try { for (Object item : map.values()) ensureAcyclic(item, ancestors, source, depth + 1); }
            finally { ancestors.remove(map); }
        } else if (value instanceof List<?> list) {
            if (ancestors.put(list, Boolean.TRUE) != null) throw new IOException("Recursive YAML aliases are not supported in " + source);
            try { for (Object item : list) ensureAcyclic(item, ancestors, source, depth + 1); }
            finally { ancestors.remove(list); }
        }
    }

    private static Map<String, Object> nest(Map<String, ?> flat) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        flat.forEach((path, value) -> {
            String[] parts = path.split("\\.");
            Map<String, Object> current = root;
            for (int i = 0; i < parts.length - 1; i++) {
                Object prior = current.get(parts[i]);
                if (prior != null && !(prior instanceof Map<?, ?>)) throw new IllegalArgumentException("Path collides with scalar: " + path);
                if (prior == null) { LinkedHashMap<String, Object> next = new LinkedHashMap<>(); current.put(parts[i], next); current = next; }
                else current = (Map<String, Object>) prior;
            }
            current.put(parts[parts.length - 1], value);
        });
        return root;
    }

    private static String safeMessage(RuntimeException exception) {
        return exception instanceof com.trickshotmlg.devkit.config.ConfigValidationException ? exception.getMessage() : exception.getClass().getSimpleName();
    }

    private static int readVersion(Object raw, Path file) throws YamlConfigException {
        if (raw == null) return 1;
        try {
            int version = com.trickshotmlg.devkit.config.ConfigCodecs.INTEGER.decode(raw);
            if (version < 1) throw new IllegalArgumentException("version must be positive");
            return version;
        } catch (RuntimeException exception) {
            throw new YamlConfigException("Invalid config-version in " + file + ": expected a positive integer", exception, List.of());
        }
    }

    static Map<String, Object> freezeUnknownValues(Map<String, ?> values) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        values.forEach((path, value) -> copy.put(path, freeze(value)));
        return Collections.unmodifiableMap(copy);
    }

    static boolean valuesEqual(Object first, Object second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (first instanceof byte[] a && second instanceof byte[] b) return java.util.Arrays.equals(a, b);
        if (first instanceof Map<?, ?> a && second instanceof Map<?, ?> b) {
            if (!a.keySet().equals(b.keySet())) return false;
            for (Object key : a.keySet()) if (!valuesEqual(a.get(key), b.get(key))) return false;
            return true;
        }
        if (first instanceof List<?> a && second instanceof List<?> b) {
            if (a.size() != b.size()) return false;
            for (int index = 0; index < a.size(); index++) if (!valuesEqual(a.get(index), b.get(index))) return false;
            return true;
        }
        if (first instanceof java.util.Set<?> a && second instanceof java.util.Set<?> b) return a.equals(b);
        return first.equals(second);
    }

    static Object freeze(Object value) {
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(freeze(key), freeze(item)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) return list.stream().map(YamlConfigFile::freeze).toList();
        if (value instanceof java.util.Set<?> set) {
            java.util.LinkedHashSet<Object> copy = new java.util.LinkedHashSet<>();
            set.forEach(item -> copy.add(freeze(item)));
            return Collections.unmodifiableSet(copy);
        }
        if (value instanceof java.util.Date date) return new java.util.Date(date.getTime());
        if (value instanceof byte[] bytes) return bytes.clone();
        return value;
    }

    private static String fingerprint(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
}
