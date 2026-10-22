package com.trickshotmlg.devkit.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Thread-safe mutable handle that atomically publishes immutable configuration snapshots. */
public final class ConfigStore {
    private final ConfigSchema schema;
    private final AtomicReference<ConfigSnapshot> active;
    private final Object mutationLock = new Object();
    private final List<Consumer<ConfigChange<?>>> listeners = new CopyOnWriteArrayList<>();
    private volatile long persistedGeneration;
    private volatile Consumer<RuntimeException> listenerFailureHandler = ignored -> { };

    private ConfigStore(ConfigSchema schema, Map<String, Object> initial, long persistedGeneration) {
        this.schema = Objects.requireNonNull(schema, "schema");
        Map<String, Object> validated = validateValues(schema, initial, true);
        active = new AtomicReference<>(new ConfigSnapshot(schema, validated, 0));
        this.persistedGeneration = persistedGeneration;
    }

    /** Creates a clean store initialized from schema defaults; required keys must have defaults. */
    public static ConfigStore defaults(ConfigSchema schema) { return new ConfigStore(schema, Map.of(), 0); }

    /** Creates a store from canonical decoded values; intended for source adapters. */
    public static ConfigStore fromValues(ConfigSchema schema, Map<String, ?> values) {
        return new ConfigStore(schema, new LinkedHashMap<>(values), 0);
    }

    public ConfigSchema schema() { return schema; }
    public ConfigSnapshot snapshot() { return active.get(); }
    public <T> T get(ConfigKey<T> key) { return snapshot().get(key); }
    public long generation() { return snapshot().generation(); }
    public boolean isDirty() {
        synchronized (mutationLock) {
            return active.get().generation() != persistedGeneration;
        }
    }

    /** Atomically applies one validated value and marks the store dirty. */
    public <T> void set(ConfigKey<T> key, T value) { update(transaction -> transaction.set(key, value)); }

    /** Applies and validates all transaction updates before publishing any of them. */
    public void update(Consumer<Transaction> operation) {
        Objects.requireNonNull(operation, "operation");
        rejectReentrantMutation();
        List<ConfigChange<?>> changes;
        synchronized (mutationLock) {
            ConfigSnapshot previous = active.get();
            LinkedHashMap<String, Object> candidate = new LinkedHashMap<>(previous.values());
            Transaction transaction = new Transaction(candidate);
            operation.accept(transaction);
            Map<String, Object> validated = validateValues(schema, candidate, true);
            changes = changes(previous, validated, ConfigChange.Origin.PROGRAMMATIC, previous.generation() + 1);
            if (!changes.isEmpty()) active.set(new ConfigSnapshot(schema, validated, previous.generation() + 1));
        }
        notifyListeners(changes);
    }

    /** Registers a listener; the returned handle removes it. Callbacks run on the publishing thread. */
    public AutoCloseable onChange(Consumer<ConfigChange<?>> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    /** Registers a listener for one typed key. */
    public <T> AutoCloseable onChange(ConfigKey<T> key, Consumer<ConfigChange<T>> listener) {
        requireKey(key);
        Objects.requireNonNull(listener, "listener");
        Consumer<ConfigChange<?>> adapter = change -> {
            if (change.key() == key) {
                @SuppressWarnings("unchecked") ConfigChange<T> typed = (ConfigChange<T>) change;
                listener.accept(typed);
            }
        };
        return onChange(adapter);
    }

    /** Sets a safe callback for listener failures; failures never undo published state. */
    public void listenerFailureHandler(Consumer<RuntimeException> handler) {
        listenerFailureHandler = Objects.requireNonNull(handler, "handler");
    }

    /** Marks current generation as persisted after a successful adapter save. */
    public void markPersisted(long savedGeneration) {
        rejectReentrantMutation();
        synchronized (mutationLock) {
            if (generation() != savedGeneration) throw new IllegalStateException("Configuration changed during save");
            persistedGeneration = savedGeneration;
        }
    }

    /** Marks the current generation dirty without changing effective values (for migrated documents). */
    public void markUnsaved() {
        rejectReentrantMutation();
        synchronized (mutationLock) {
            ConfigSnapshot current = active.get();
            active.set(new ConfigSnapshot(schema, current.values(), current.generation() + 1));
        }
    }

    /** Source adapters atomically replace a fully validated candidate as one reload operation. */
    public void replaceFromReload(Map<String, ?> values) {
        replaceFromReload(values, generation());
    }

    /** Atomically replaces the store only if no programmatic update happened since the caller's read. */
    public void replaceFromReload(Map<String, ?> values, long expectedGeneration) {
        rejectReentrantMutation();
        List<ConfigChange<?>> changes;
        synchronized (mutationLock) {
            ConfigSnapshot previous = active.get();
            if (previous.generation() != expectedGeneration) {
                throw new java.util.ConcurrentModificationException("Configuration changed while a reload candidate was being prepared");
            }
            Map<String, Object> validated = validateValues(schema, new LinkedHashMap<>(values), true);
            long nextGeneration = previous.generation() + 1;
            changes = changes(previous, validated, ConfigChange.Origin.RELOAD, nextGeneration);
            active.set(new ConfigSnapshot(schema, validated, nextGeneration));
            persistedGeneration = nextGeneration;
        }
        notifyListeners(changes);
    }

    private void notifyListeners(List<ConfigChange<?>> changes) {
        for (ConfigChange<?> change : changes) for (Consumer<ConfigChange<?>> listener : listeners) {
            try { listener.accept(change); } catch (RuntimeException exception) {
                try { listenerFailureHandler.accept(exception); } catch (RuntimeException ignored) { /* published state remains authoritative */ }
            }
        }
    }

    private List<ConfigChange<?>> changes(ConfigSnapshot old, Map<String, Object> values,
                                           ConfigChange.Origin origin, long generation) {
        ArrayList<ConfigChange<?>> result = new ArrayList<>();
        for (ConfigKey<?> key : schema.keys().values()) {
            Object before = old.values().get(key.path());
            Object after = values.get(key.path());
            if (!Objects.equals(before, after)) result.add(change(key, before, after, origin, generation));
        }
        return List.copyOf(result);
    }

    private static <T> ConfigChange<T> change(ConfigKey<T> key, Object before, Object after,
                                               ConfigChange.Origin origin, long generation) {
        @SuppressWarnings("unchecked") T oldValue = (T) before;
        @SuppressWarnings("unchecked") T newValue = (T) after;
        return new ConfigChange<>(key, oldValue, newValue, origin, generation);
    }

    private void requireKey(ConfigKey<?> key) {
        if (schema.key(key.path()).orElse(null) != key) throw new IllegalArgumentException("Key is not registered in this schema: " + key.path());
    }

    /**
     * Mutation callbacks and validators run while the mutation monitor is held. Java monitors are
     * reentrant, so allowing a nested store mutation would publish an inner state that the outer
     * transaction could then overwrite using its stale candidate map.
     */
    private void rejectReentrantMutation() {
        if (Thread.holdsLock(mutationLock)) {
            throw new IllegalStateException("Nested configuration mutations are not supported; update the active transaction instead");
        }
    }

    static Map<String, Object> validateValues(ConfigSchema schema, Map<String, ?> input, boolean rejectUnknown) {
        ArrayList<ConfigIssue> issues = new ArrayList<>();
        if (rejectUnknown) for (String path : input.keySet()) if (schema.key(path).isEmpty()) issues.add(ConfigIssue.error(path, "unknown configuration key"));
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (ConfigKey<?> key : schema.keys().values()) {
            Object raw = input.get(key.path());
            if (raw == null && !input.containsKey(key.path())) {
                if (key.defaultValue().isPresent()) values.put(key.path(), key.defaultValue().orElseThrow());
                else issues.add(ConfigIssue.error(key.path(), "required value is missing"));
                continue;
            }
            if (raw == null) { issues.add(ConfigIssue.error(key.path(), "null is not allowed")); continue; }
            try { decodeAndValidate(key, raw, values, issues); }
            catch (RuntimeException exception) { issues.add(ConfigIssue.error(key.path(), key.sensitive() ? "invalid sensitive value; expected " + key.codec().description() : exception.getMessage())); }
        }
        if (issues.stream().noneMatch(issue -> issue.severity() == ConfigIssue.Severity.ERROR)) {
            ConfigValues view = new ConfigValues() {
                @Override public <T> T get(ConfigKey<T> key) {
                    ConfigKey<?> registered = schema.key(key.path()).orElseThrow(() -> new IllegalArgumentException("Key is not in schema: " + key.path()));
                    if (registered != key) throw new IllegalArgumentException("Different key declaration for " + key.path());
                    @SuppressWarnings("unchecked") T value = (T) values.get(key.path());
                    return value;
                }
            };
            for (ConfigConstraint constraint : schema.constraints()) {
                try {
                    for (ConfigIssue issue : constraint.validate(view)) {
                        ConfigKey<?> key = schema.key(issue.path()).orElse(null);
                        issues.add(sanitizeConstraintIssue(issue, key, schema, values));
                    }
                } catch (RuntimeException exception) {
                    // Cross-field validators can inspect every value, so their exception messages
                    // cannot be assumed safe even when the failing key is not identified.
                    issues.add(ConfigIssue.error("", "cross-field validator failed"));
                }
            }
        }
        if (issues.stream().anyMatch(issue -> issue.severity() == ConfigIssue.Severity.ERROR)) throw new ConfigValidationException(issues);
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static ConfigIssue sanitizeConstraintIssue(ConfigIssue issue, ConfigKey<?> issueKey,
                                                       ConfigSchema schema, Map<String, Object> values) {
        if (issueKey != null && issueKey.sensitive()) {
            return new ConfigIssue(issue.path(), issue.severity(), "invalid sensitive value", issue.source(), java.util.Optional.empty());
        }
        String message = issue.message();
        String expected = issue.expected().orElse(null);
        for (ConfigKey<?> key : schema.keys().values()) {
            if (!key.sensitive()) continue;
            Object value = values.get(key.path());
            if (value == null) continue;
            String sensitiveText;
            try { sensitiveText = String.valueOf(value); }
            catch (RuntimeException ignored) { continue; }
            if (!sensitiveText.isEmpty()) {
                message = message.replace(sensitiveText, "[REDACTED]");
                if (expected != null) expected = expected.replace(sensitiveText, "[REDACTED]");
            }
        }
        return new ConfigIssue(issue.path(), issue.severity(), message, issue.source(),
                java.util.Optional.ofNullable(expected));
    }

    private static <T> void decodeAndValidate(ConfigKey<T> key, Object raw, Map<String, Object> values, List<ConfigIssue> issues) {
        T decoded = ConfigCodecs.freeze(key.codec().decode(raw));
        for (String error : key.validate(decoded)) issues.add(ConfigIssue.error(key.path(), key.sensitive() ? "invalid sensitive value" : error));
        values.put(key.path(), decoded);
    }

    /** Mutable transaction valid only during one {@link #update(Consumer)} call. */
    public final class Transaction {
        private final Map<String, Object> values;
        private Transaction(Map<String, Object> values) { this.values = values; }
        public <T> Transaction set(ConfigKey<T> key, T value) {
            requireKey(key);
            Objects.requireNonNull(value, "value");
            try {
                values.put(key.path(), ConfigCodecs.freeze(key.codec().decode(key.codec().encode(value))));
            } catch (RuntimeException exception) {
                if (key.sensitive()) throw new IllegalArgumentException("Invalid sensitive value for configuration key '" + key.path() + "'");
                throw exception;
            }
            return this;
        }
    }
}
