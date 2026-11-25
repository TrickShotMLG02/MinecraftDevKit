package com.trickshotmlg.devkit.cooldowns;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CooldownManagerTest {
    @Test void acquisitionExpiresAtExactBoundaryAndCanBeRemoved() {
        AtomicLong time = new AtomicLong(10);
        CooldownManager<String> manager = CooldownManager.withClock(time::get);
        assertTrue(manager.tryAcquire("player", Duration.ofNanos(5)));
        assertFalse(manager.tryAcquire("player", Duration.ofNanos(5)));
        time.set(14);
        assertEquals(Duration.ofNanos(1), manager.remaining("player"));
        time.set(15);
        assertEquals(Duration.ZERO, manager.remaining("player"));
        assertTrue(manager.tryAcquire("player", Duration.ofNanos(1)));
        assertTrue(manager.remove("player"));
        assertFalse(manager.isActive("player"));
    }

    @Test void startReplacesRefreshOnlyExtendsAndZeroDoesNotStore() {
        AtomicLong time = new AtomicLong();
        CooldownManager<String> manager = CooldownManager.withClock(time::get);
        manager.start("a", Duration.ofNanos(10));
        manager.refresh("a", Duration.ofNanos(5));
        assertEquals(Duration.ofNanos(10), manager.remaining("a"));
        manager.refresh("a", Duration.ofNanos(20));
        assertEquals(Duration.ofNanos(20), manager.remaining("a"));
        manager.start("a", Duration.ofNanos(3));
        assertEquals(Duration.ofNanos(3), manager.remaining("a"));
        assertTrue(manager.tryAcquire("zero", Duration.ZERO));
        assertEquals(1, manager.size());
    }

    @Test void cleanupIsLazyAndDurationIsValidated() {
        AtomicLong time = new AtomicLong();
        CooldownManager<Integer> manager = CooldownManager.withClock(time::get);
        manager.start(1, Duration.ofNanos(2));
        manager.start(2, Duration.ofNanos(3));
        time.set(2);
        assertEquals(1, manager.cleanupExpired());
        assertEquals(1, manager.size());
        assertThrows(IllegalArgumentException.class, () -> manager.start(3, Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class, () -> manager.start(3, Duration.ofSeconds(Long.MAX_VALUE)));
    }

    @Test void onlyOneConcurrentAcquisitionWins() throws Exception {
        CooldownManager<String> manager = CooldownManager.create();
        int workers = 24;
        var pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch go = new CountDownLatch(1);
        try {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < workers; i++) results.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(go.await(2, TimeUnit.SECONDS));
                return manager.tryAcquire("same", Duration.ofMinutes(1));
            }));
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            go.countDown();
            int accepted = 0;
            for (var result : results) if (result.get(2, TimeUnit.SECONDS)) accepted++;
            assertEquals(1, accepted);
        } finally {
            pool.shutdownNow();
            manager.close();
        }
    }

    @Test void closedManagerRejectsFurtherOperations() {
        CooldownManager<String> manager = CooldownManager.create();
        manager.close();
        manager.close();
        assertThrows(IllegalStateException.class, () -> manager.tryAcquire("x", Duration.ZERO));
    }
}
