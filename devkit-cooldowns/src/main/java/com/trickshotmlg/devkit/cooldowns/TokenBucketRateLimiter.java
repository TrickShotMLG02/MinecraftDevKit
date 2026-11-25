package com.trickshotmlg.devkit.cooldowns;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongSupplier;

/** Per-key token bucket limiter. A bucket starts full and refills continuously at a fixed rate. */
public final class TokenBucketRateLimiter<K> implements AutoCloseable {
    private record Bucket(double tokens, long lastRefillNanos, long lastTouchedNanos) { }
    private final ConcurrentHashMap<K, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final int refillTokens;
    private final long refillPeriodNanos;
    private final double tokensPerNano;
    private final LongSupplier nanoClock;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();

    /**
     * Creates a limiter with initially-full per-key buckets and continuous refill.
     * @param capacity maximum tokens per key
     * @param refillTokens tokens added per period
     * @param refillPeriod time for one refill interval
     */
    public TokenBucketRateLimiter(int capacity, int refillTokens, Duration refillPeriod) {
        this(capacity, refillTokens, refillPeriod, System::nanoTime);
    }

    private TokenBucketRateLimiter(int capacity, int refillTokens, Duration refillPeriod, LongSupplier nanoClock) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        if (refillTokens < 1) throw new IllegalArgumentException("refillTokens must be positive");
        long period = CooldownManager.durationNanos(refillPeriod, false);
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodNanos = period;
        this.tokensPerNano = (double) refillTokens / period;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    /** Injectable monotonic time source for deterministic tests.
     * @param capacity maximum tokens per key
     * @param refillTokens tokens added per period
     * @param refillPeriod time for one refill interval
     * @param nanoClock monotonic elapsed-time source
     * @param <K> key type
     * @return a new limiter
     */
    public static <K> TokenBucketRateLimiter<K> withClock(int capacity, int refillTokens,
                                                           Duration refillPeriod, LongSupplier nanoClock) {
        return new TokenBucketRateLimiter<>(capacity, refillTokens, refillPeriod, nanoClock);
    }

    /** Atomically consumes one token when the key has one available.
     * @param key application-owned key
     * @return true when a token was consumed
     */
    public boolean tryAcquire(K key) {
        Objects.requireNonNull(key, "key");
        return mutate(key, true).allowed;
    }

    /** Whole-token allowance after applying any elapsed refill.
     * @param key application-owned key
     * @return available whole tokens
     */
    public int remaining(K key) { return (int) Math.floor(mutate(key, false).bucket.tokens); }

    /** Removes the key's accumulated bucket state.
     * @param key application-owned key
     * @return true if state existed
     */
    public boolean remove(K key) {
        Objects.requireNonNull(key, "key");
        lifecycle.readLock().lock();
        try { ensureOpen(); return buckets.remove(key) != null; }
        finally { lifecycle.readLock().unlock(); }
    }

    /** Time until one token is available; zero if acquisition is currently possible.
     * @param key application-owned key
     * @return retry delay
     */
    public Duration retryAfter(K key) {
        Bucket current = mutate(key, false).bucket;
        double missing = 1.0 - current.tokens;
        if (missing <= 0) return Duration.ZERO;
        double wait = Math.ceil(missing / tokensPerNano);
        return Duration.ofNanos(wait >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) wait);
    }

    private record Result(Bucket bucket, boolean allowed) { }

    private Result mutate(K key, boolean acquire) {
        lifecycle.readLock().lock();
        try {
            ensureOpen();
            long now = nanoClock.getAsLong();
            Result[] result = new Result[1];
            buckets.compute(key, (ignored, existing) -> {
                Bucket updated = refill(existing, now);
                boolean allowed = !acquire || updated.tokens >= 1.0;
                double tokens = acquire && allowed ? updated.tokens - 1.0 : updated.tokens;
                Bucket touched = new Bucket(tokens, updated.lastRefillNanos, now);
                result[0] = new Result(touched, allowed);
                return touched;
            });
            return result[0];
        } finally { lifecycle.readLock().unlock(); }
    }

    private Bucket refill(Bucket current, long now) {
        if (current == null) return new Bucket(capacity, now, now);
        long elapsed = now - current.lastRefillNanos;
        if (elapsed <= 0 || current.tokens >= capacity) {
            return new Bucket(Math.min(capacity, current.tokens), elapsed > 0 ? now : current.lastRefillNanos, now);
        }
        double tokens = Math.min(capacity, current.tokens + elapsed * tokensPerNano);
        long lastRefill = tokens >= capacity ? now : current.lastRefillNanos;
        return new Bucket(tokens, lastRefill, now);
    }

    /** Removes keys untouched for at least {@code idleFor}; use periodically or on bounded key churn.
     * @param idleFor positive inactivity interval
     * @return number of removed keys
     */
    public int cleanupIdle(Duration idleFor) {
        long idleNanos = CooldownManager.durationNanos(idleFor, false);
        lifecycle.readLock().lock();
        try {
            ensureOpen();
            long now = nanoClock.getAsLong();
            int[] removed = {0};
            buckets.forEach((key, bucket) -> {
                if (now - bucket.lastTouchedNanos >= idleNanos && buckets.remove(key, bucket)) removed[0]++;
            });
            return removed[0];
        } finally { lifecycle.readLock().unlock(); }
    }

    /** Number of currently retained bucket keys.
     * @return retained key count
     */
    public int size() { lifecycle.readLock().lock(); try { ensureOpen(); return buckets.size(); }
        finally { lifecycle.readLock().unlock(); } }

    /** Removes all per-key state while keeping this limiter open. */
    public void clear() { lifecycle.readLock().lock(); try { ensureOpen(); buckets.clear(); }
        finally { lifecycle.readLock().unlock(); } }

    @Override public void close() {
        lifecycle.writeLock().lock();
        try { if (closed.compareAndSet(false, true)) buckets.clear(); }
        finally { lifecycle.writeLock().unlock(); }
    }

    private void ensureOpen() { if (closed.get()) throw new IllegalStateException("TokenBucketRateLimiter is closed"); }
}
