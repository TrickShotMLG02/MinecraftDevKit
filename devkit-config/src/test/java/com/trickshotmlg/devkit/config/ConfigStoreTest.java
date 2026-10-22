package com.trickshotmlg.devkit.config;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ConfigStoreTest {
    private static final ConfigKey<Boolean> ENABLED = ConfigKey.bool("service.enabled", true).build();
    private static final ConfigKey<Integer> LIMIT = ConfigKey.integer("service.limit", 10).range(1, 20).build();
    private static final ConfigSchema SCHEMA = ConfigSchema.builder().register(ENABLED, LIMIT)
            .validate(values -> values.get(ENABLED) && values.get(LIMIT) < 2
                    ? java.util.List.of(ConfigIssue.error(LIMIT.path(), "must be at least 2 when enabled")) : java.util.List.of())
            .build();

    @Test void transactionsAreAtomicAndDirtyStateTracksPersistedGeneration() {
        ConfigStore store = ConfigStore.defaults(SCHEMA);
        ConfigSnapshot before = store.snapshot();
        assertFalse(store.isDirty());
        assertThrows(ConfigValidationException.class, () -> store.update(tx -> {
            tx.set(ENABLED, true);
            tx.set(LIMIT, 1);
        }));
        assertEquals(10, store.get(LIMIT));
        assertEquals(0, store.generation());
        store.update(tx -> { tx.set(ENABLED, false); tx.set(LIMIT, 1); });
        assertEquals(1, store.get(LIMIT));
        assertEquals(10, before.get(LIMIT));
        assertTrue(store.isDirty());
        long saved = store.generation();
        store.markPersisted(saved);
        assertFalse(store.isDirty());
    }

    @Test void typedReadsAndChangeListenersReflectOnlyEffectiveChanges() throws Exception {
        ConfigStore store = ConfigStore.defaults(SCHEMA);
        AtomicInteger calls = new AtomicInteger();
        var subscription = store.onChange(LIMIT, event -> {
            calls.incrementAndGet();
            assertEquals(10, event.oldValue());
            assertEquals(15, event.newValue());
            assertEquals(ConfigChange.Origin.PROGRAMMATIC, event.origin());
        });
        store.set(LIMIT, 10);
        assertEquals(0, calls.get());
        store.set(LIMIT, 15);
        assertEquals(1, calls.get());
        subscription.close();
        store.set(LIMIT, 16);
        assertEquals(1, calls.get());
        assertThrows(IllegalArgumentException.class, () -> store.get(ConfigKey.integer("service.limit", 2).build()));
    }

    @Test void requiredValuesAndSensitiveDiagnosticsDoNotLeakValues() {
        ConfigKey<String> secret = ConfigKey.string("database.password").sensitive().build();
        ConfigSchema schema = ConfigSchema.builder().register(secret).build();
        ConfigValidationException missing = assertThrows(ConfigValidationException.class, () -> ConfigStore.defaults(schema));
        assertTrue(missing.getMessage().contains("required value is missing"));
        ConfigValidationException invalid = assertThrows(ConfigValidationException.class,
                () -> ConfigStore.fromValues(schema, Map.of(secret.path(), 42)));
        assertFalse(invalid.getMessage().contains("42"));
    }
}
