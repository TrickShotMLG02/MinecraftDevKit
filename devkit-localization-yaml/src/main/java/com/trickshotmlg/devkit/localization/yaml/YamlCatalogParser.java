package com.trickshotmlg.devkit.localization.yaml;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Safe parser that flattens a YAML mapping into dot-separated string translations. */
public final class YamlCatalogParser {
    private static final int MAX_BYTES = 1_048_576;
    private YamlCatalogParser() { }

    /** Parses one UTF-8 YAML document, rejecting non-string leaves and duplicate keys. */
    public static Map<String, String> parse(InputStream input, String source) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("Translation YAML exceeds 1 MiB: " + source);
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(20);
        options.setNestingDepthLimit(32);
        options.setCodePointLimit(MAX_BYTES);
        try {
            Object root = new Yaml(new SafeConstructor(options)).load(new InputStreamReader(
                    new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
            if (root == null) return Map.of();
            if (!(root instanceof Map<?, ?> mapping)) throw new IOException("YAML root must be a mapping: " + source);
            TreeMap<String, String> flattened = new TreeMap<>();
            flatten(mapping, "", flattened, source, new IdentityHashMap<>());
            return Collections.unmodifiableMap(flattened);
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid translation YAML in " + source + ": " + exception.getMessage(), exception);
        }
    }

    private static void flatten(Map<?, ?> mapping, String prefix, Map<String, String> output, String source,
                                IdentityHashMap<Map<?, ?>, Boolean> ancestors) throws IOException {
        if (ancestors.put(mapping, Boolean.TRUE) != null) {
            throw new IOException("Cyclic YAML aliases are not supported in " + source
                    + (prefix.isEmpty() ? "" : " at '" + prefix + "'"));
        }
        try {
            for (Map.Entry<?, ?> entry : mapping.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.isBlank() || key.contains(".")) {
                    throw new IOException("YAML translation keys must be non-empty strings without dots in " + source + ": " + entry.getKey());
                }
                String path = prefix.isEmpty() ? key : prefix + "." + key;
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> child) {
                    flatten(child, path, output, source, ancestors);
                } else if (value instanceof String text) {
                    if (output.putIfAbsent(path, text) != null) throw new IOException("Flattened duplicate key '" + path + "' in " + source);
                } else {
                    throw new IOException("Translation value for '" + path + "' must be a quoted YAML string in " + source
                            + " (found " + (value == null ? "null" : value.getClass().getSimpleName()) + ")");
                }
            }
        } finally {
            ancestors.remove(mapping);
        }
    }
}
