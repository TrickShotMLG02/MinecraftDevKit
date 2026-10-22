package com.trickshotmlg.devkit.config.yaml;

import java.util.Map;
import java.util.Objects;

/** Controlled migration view over raw flattened YAML values; never references the active snapshot. */
public final class MutableConfigValues {
    private final Map<String, Object> values;
    MutableConfigValues(Map<String, Object> values) { this.values = values; }
    public boolean contains(String path) { return values.containsKey(validate(path)); }
    public Object get(String path) { return values.get(validate(path)); }
    public void set(String path, Object value) { values.put(validate(path), Objects.requireNonNull(value, "value")); }
    public Object remove(String path) { return values.remove(validate(path)); }
    public void move(String from, String to) {
        from = validate(from); to = validate(to);
        if (!values.containsKey(from)) return;
        if (values.containsKey(to)) throw new IllegalArgumentException("Cannot migrate '" + from + "' to existing path '" + to + "'");
        values.put(to, values.remove(from));
    }
    public Map<String, Object> view() { return java.util.Collections.unmodifiableMap(values); }
    private static String validate(String path) {
        Objects.requireNonNull(path, "path");
        if (path.isBlank() || path.equals("config-version") || path.startsWith(".") || path.endsWith("."))
            throw new IllegalArgumentException("Invalid migration path: " + path);
        for (String part : path.split("\\.", -1)) if (!part.matches("[A-Za-z_][A-Za-z0-9_-]*"))
            throw new IllegalArgumentException("Invalid migration path: " + path);
        return path;
    }
}
