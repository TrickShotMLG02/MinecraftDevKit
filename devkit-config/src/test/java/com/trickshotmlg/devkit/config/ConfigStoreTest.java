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

    @Test void nestedUpdateCannotPublishAChangeThatTheOuterTransactionThenLoses() {
        ConfigStore store = ConfigStore.defaults(SCHEMA);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> store.update(transaction -> {
            transaction.set(LIMIT, 15);
            store.set(ENABLED, false);
        }));

        assertTrue(failure.getMessage().contains("Nested"));
        assertEquals(10, store.get(LIMIT));
        assertTrue(store.get(ENABLED));
        assertEquals(0, store.generation());
        assertFalse(store.isDirty());
    }

    @Test void sensitiveDefaultAndProgrammaticCodecFailuresDoNotExposeValues() {
        String secret = "private-token-7f9a";
        IllegalArgumentException invalidDefault = assertThrows(IllegalArgumentException.class, () ->
                ConfigKey.string("database.password", secret)
                        .sensitive()
                        .validate(value -> java.util.Optional.of("rejected value: " + value))
                        .build());
        assertFalse(invalidDefault.getMessage().contains(secret));

        ConfigCodec<String> codec = ConfigCodecs.of("credential", raw -> {
            if ("safe".equals(raw)) return "safe";
            throw new IllegalArgumentException("rejected value: " + raw);
        }, value -> value);
        ConfigKey<String> password = ConfigKey.builder("database.password", codec)
                .defaultValue("safe").sensitive().build();
        ConfigStore store = ConfigStore.defaults(ConfigSchema.builder().register(password).build());
        IllegalArgumentException invalidUpdate = assertThrows(IllegalArgumentException.class,
                () -> store.set(password, secret));
        assertFalse(invalidUpdate.getMessage().contains(secret));
        assertEquals("safe", store.get(password));
    }

    @Test void sensitiveCrossFieldValidationMessagesAreRedacted() {
        String secret = "private-token-7f9a";
        ConfigKey<String> password = ConfigKey.string("database.password", secret).sensitive().build();
        ConfigSchema schema = ConfigSchema.builder().register(password)
                .validate(values -> java.util.List.of(ConfigIssue.error(password.path(),
                        "invalid credential " + values.get(password))))
                .build();

        ConfigValidationException failure = assertThrows(ConfigValidationException.class,
                () -> ConfigStore.defaults(schema));

        assertFalse(failure.getMessage().contains(secret));
        assertTrue(failure.getMessage().contains("database.password"));
    }
}
