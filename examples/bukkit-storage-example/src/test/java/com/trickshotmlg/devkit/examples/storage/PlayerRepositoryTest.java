package com.trickshotmlg.devkit.examples.storage;

import com.trickshotmlg.devkit.storage.Storage;
import com.trickshotmlg.devkit.storage.migrations.StorageMigrations;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerRepositoryTest {
    @Test void migrationsAndAtomicConcurrentUpdatesUseTypedAsyncStorage() throws Exception {
        try (Storage storage = Storage.sqliteMemory()) {
            var report = StorageMigrations.builder(storage).locations("classpath:db/migration").migrate();
            assertEquals(1, report.migrationsExecuted());
            PlayerRepository repository = new PlayerRepository(storage);
            UUID player = UUID.randomUUID();
            int writers = 4;
            int increments = 25;
            try (var executor = Executors.newFixedThreadPool(writers)) {
                List<java.util.concurrent.Future<?>> work = new ArrayList<>();
                for (int writer = 0; writer < writers; writer++) {
                    work.add(executor.submit(() -> {
                        for (int i = 0; i < increments; i++) {
                            try {
                                repository.add(player, 1).result().toCompletableFuture().get(10, TimeUnit.SECONDS);
                            } catch (Exception failure) {
                                throw new RuntimeException(failure);
                            }
                        }
                    }));
                }
                for (var future : work) future.get(20, TimeUnit.SECONDS);
            }
            assertEquals((long) writers * increments,
                    repository.balance(player).result().toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow());
            assertEquals(1, repository.set(player, 250).result().toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertEquals(250L, repository.balance(player).result().toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow());
        }
    }
}
