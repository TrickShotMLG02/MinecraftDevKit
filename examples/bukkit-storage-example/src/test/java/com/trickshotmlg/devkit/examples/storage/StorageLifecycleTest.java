package com.trickshotmlg.devkit.examples.storage;

import com.trickshotmlg.devkit.lifecycle.ComponentKey;
import com.trickshotmlg.devkit.lifecycle.LifecycleException;
import com.trickshotmlg.devkit.lifecycle.LifecycleManager;
import com.trickshotmlg.devkit.lifecycle.ServiceKey;
import com.trickshotmlg.devkit.storage.Storage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StorageLifecycleTest {
    @Test void migrationStartupFailureRollsBackAndClosesStorage() {
        ServiceKey<Storage> storageKey = ServiceKey.of("test-storage", Storage.class);
        ComponentKey failingMigration = ComponentKey.of("failing-migration");
        AtomicReference<Storage> created = new AtomicReference<>();
        LifecycleManager lifecycle = LifecycleManager.builder()
                .service(storageKey, context -> {
                    Storage storage = Storage.sqliteMemory();
                    created.set(storage);
                    return storage;
                })
                .component(failingMigration, context -> {
                    context.require(storageKey);
                    throw new IllegalStateException("controlled migration failure");
                }, null, storageKey)
                .build();

        assertThrows(LifecycleException.class, lifecycle::start);
        assertEquals(com.trickshotmlg.devkit.lifecycle.LifecycleState.FAILED, lifecycle.state());
        assertNotNull(created.get());
        assertTrue(created.get().isClosed());
        lifecycle.close();
        assertTrue(created.get().isClosed());
    }

    @Test void databaseTaskScopeClosesBeforeStorageItHandsResultsThrough() {
        ServiceKey<Storage> storageKey = ServiceKey.of("scope-test-storage", Storage.class);
        ServiceKey<AutoCloseable> taskScope = ServiceKey.of("scope-test-tasks", AutoCloseable.class);
        LifecycleManager lifecycle = LifecycleManager.builder()
                .service(storageKey, context -> Storage.sqliteMemory())
                .service(taskScope, context -> {
                    Storage storage = context.require(storageKey);
                    return () -> assertFalse(storage.isClosed(), "Storage must outlive its async continuation scope");
                }, storageKey)
                .build();

        lifecycle.start();
        Storage storage = lifecycle.get(storageKey);
        lifecycle.close();
        assertTrue(storage.isClosed());
    }
}
