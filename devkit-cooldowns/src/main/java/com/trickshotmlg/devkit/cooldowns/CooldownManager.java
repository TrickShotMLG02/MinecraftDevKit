package com.trickshotmlg.devkit.cooldowns;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongSupplier;

/** Thread-safe fixed-duration cooldowns keyed by application-owned identifiers. */
public final class CooldownManager<K> implements AutoCloseable {
    private final ConcurrentHashMap<K, Long> deadlines = new ConcurrentHashMap<>();
    private final LongSupplier nanoClock;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();

    private CooldownManager(LongSupplier nanoClock) { this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock"); }

    /**
     * Creates a manager using monotonic system time.
     * @param <K> key type
     * @return an empty cooldown manager
     */
    public static <K> CooldownManager<K> create() { return new CooldownManager<>(System::nanoTime); }
    /**
     * Creates a manager with an injected monotonic time source, primarily for deterministic tests.
     * @param nanoClock monotonic elapsed-time source
     * @param <K> key type
     * @return an empty cooldown manager
     */
    public static <K> CooldownManager<K> withClock(LongSupplier nanoClock) { return new CooldownManager<>(nanoClock); }

    /** Atomically starts a cooldown if absent or expired. Zero duration always succeeds without storing a key.
     * @param key application-owned key
     * @param duration non-negative cooldown duration
     * @return whether the action acquired the cooldown
     */
    public boolean tryAcquire(K key, Duration duration) {
        Objects.requireNonNull(key, "key");
        long nanos = durationNanos(duration, true);
        lifecycle.readLock().lock();
        try {
            ensureOpen();
            if (nanos == 0) return true;
            long now = nanoClock.getAsLong();
            AtomicBoolean acquired = new AtomicBoolean();
            deadlines.compute(key, (ignored, deadline) -> {
                if (deadline == null || deadline - now <= 0) {
                    acquired.set(true);
                    return now + nanos;
                }
                return deadline;
            });
            return acquired.get();
        } finally { lifecycle.readLock().unlock(); }
    }

    /** Starts or replaces the key's deadline, even if a cooldown is already active.
     * A zero duration removes the key.
     * @param key application-owned key
     * @param duration non-negative cooldown duration
     */
    public void start(K key, Duration duration) {
        Objects.requireNonNull(key, "key");
        long nanos = durationNanos(duration, true);
        lifecycle.readLock().lock();
        try {
            ensureOpen();
            if (nanos == 0) deadlines.remove(key);
            else deadlines.put(key, nanoClock.getAsLong() + nanos);
        } finally { lifecycle.readLock().unlock(); }
    }

    /** Replaces the deadline only when it would extend the current active cooldown.
     * @param key application-owned key
     * @param duration non-negative requested duration
     */
    public void refresh(K key, Duration duration) {
        Objects.requireNonNull(key, "key");
        long nanos = durationNanos(duration, true);
        lifecycle.readLock().lock();
        try {
            ensureOpen();
            long now = nanoClock.getAsLong();
            if (nanos == 0) {
                deadlines.computeIfPresent(key, (ignored, deadline) -> deadline - now > 0 ? deadline : null);
                return;
            }
            long candidate = now + nanos;
            deadlines.compute(key, (ignored, deadline) -> deadline == null || deadline - now <= 0
                    || candidate - deadline > 0 ? candidate : deadline);
        } finally { lifecycle.readLock().unlock(); }
    }

    /** Returns whether this key currently has unexpired cooldown time.
     * @param key application-owned key
     * @return true when the cooldown is active
     */
    public boolean isActive(K key) { return !remaining(key).isZero(); }

    /** Returns zero when absent or expired and lazily removes expired state.
     * @param key application-owned key
     * @return positive remaining duration or zero
     */
    public Duration remaining(K key) {
        Objects.requireNonNull(key, "key");
        lifecycle.readLock().lock();
        try {
            ensureOpen();
            long now = nanoClock.getAsLong();
            Long deadline = deadlines.get(key);
            if (deadline == null) return Duration.ZERO;
            long remaining = deadline - now;
            if (remaining <= 0) { deadlines.remove(key, deadline); return Duration.ZERO; }
            return Duration.ofNanos(remaining);
        } finally { lifecycle.readLock().unlock(); }
    }

    /** Removes one key's cooldown.
     * @param key application-owned key
     * @return true if an entry existed
     */
    public boolean remove(K key) {
        Objects.requireNonNull(key, "key");
        lifecycle.readLock().lock();
        try { ensureOpen(); return deadlines.remove(key) != null; }
        finally { lifecycle.readLock().unlock(); }
    }

    /** Removes every cooldown without closing the manager. */
    public void clear() {
        lifecycle.readLock().lock();
        try { ensureOpen(); deadlines.clear(); }
        finally { lifecycle.readLock().unlock(); }
    }

    /** Removes expired entries without scheduling per-key cleanup tasks.
     * @return number of entries removed
     */
    public int cleanupExpired() {
        lifecycle.readLock().lock();
        try {
            ensureOpen();
            long now = nanoClock.getAsLong();
            int[] removed = {0};
            deadlines.forEach((key, deadline) -> {
                if (deadline - now <= 0 && deadlines.remove(key, deadline)) removed[0]++;
            });
            return removed[0];
        } finally { lifecycle.readLock().unlock(); }
    }

    /** Number of currently retained keys, including expired keys not yet lazily removed.
     * @return retained key count
     */
    public int size() { lifecycle.readLock().lock(); try { ensureOpen(); return deadlines.size(); }
        finally { lifecycle.readLock().unlock(); } }

    @Override public void close() {
        lifecycle.writeLock().lock();
        try { if (closed.compareAndSet(false, true)) deadlines.clear(); }
        finally { lifecycle.writeLock().unlock(); }
    }

    private void ensureOpen() { if (closed.get()) throw new IllegalStateException("CooldownManager is closed"); }

    static long durationNanos(Duration duration, boolean allowZero) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || (!allowZero && duration.isZero())) {
            throw new IllegalArgumentException("Duration must be " + (allowZero ? "non-negative" : "positive"));
        }
        try { return duration.toNanos(); }
        catch (ArithmeticException overflow) { throw new IllegalArgumentException("Duration is too large", overflow); }
    }
}
