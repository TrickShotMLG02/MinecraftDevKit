package com.trickshotmlg.devkit.cooldowns;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketRateLimiterTest {
    @Test void consumesCapacityAndRefillsContinuously() {
        AtomicLong time = new AtomicLong();
        TokenBucketRateLimiter<String> limiter = TokenBucketRateLimiter.withClock(2, 1, Duration.ofSeconds(10), time::get);
        assertEquals(2, limiter.remaining("x"));
        assertTrue(limiter.tryAcquire("x"));
        assertTrue(limiter.tryAcquire("x"));
        assertFalse(limiter.tryAcquire("x"));
        assertEquals(Duration.ofSeconds(10), limiter.retryAfter("x"));
        time.set(TimeUnitNanos.seconds(5));
        assertEquals(0, limiter.remaining("x"));
        time.set(TimeUnitNanos.seconds(10));
        assertEquals(1, limiter.remaining("x"));
        assertTrue(limiter.tryAcquire("x"));
    }

    @Test void keysAreIndependentAndIdleEntriesCanBeRemoved() {
        AtomicLong time = new AtomicLong();
        TokenBucketRateLimiter<String> limiter = TokenBucketRateLimiter.withClock(1, 1, Duration.ofSeconds(1), time::get);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
        assertEquals(2, limiter.size());
        time.set(TimeUnitNanos.seconds(2));
        assertEquals(2, limiter.cleanupIdle(Duration.ofSeconds(2)));
        assertEquals(0, limiter.size());
        assertTrue(limiter.tryAcquire("c"));
        assertTrue(limiter.remove("c"));
        assertFalse(limiter.remove("c"));
    }

    @Test void rejectsInvalidSettingsAndClosesIdempotently() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter<>(0, 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter<>(1, 0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter<>(1, 1, Duration.ZERO));
        TokenBucketRateLimiter<String> limiter = new TokenBucketRateLimiter<>(1, 1, Duration.ofSeconds(1));
        limiter.close();
        limiter.close();
        assertThrows(IllegalStateException.class, () -> limiter.tryAcquire("x"));
    }

    @Test void concurrentAcquisitionNeverExceedsBucketCapacity() throws Exception {
        TokenBucketRateLimiter<String> limiter = TokenBucketRateLimiter.withClock(
                4, 1, Duration.ofDays(1), () -> 0L);
        int workers = 24;
        var pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch go = new CountDownLatch(1);
        try {
            var results = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < workers; i++) results.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(go.await(2, TimeUnit.SECONDS));
                return limiter.tryAcquire("shared");
            }));
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            go.countDown();
            int accepted = 0;
            for (var result : results) if (result.get(2, TimeUnit.SECONDS)) accepted++;
            assertEquals(4, accepted);
        } finally {
            pool.shutdownNow();
            limiter.close();
        }
    }

    private static final class TimeUnitNanos {
        static long seconds(long value) { return value * 1_000_000_000L; }
    }
}
