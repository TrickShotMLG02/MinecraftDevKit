# devkit-cooldowns

Platform-independent, thread-safe cooldowns and per-key token-bucket rate limiting. The module uses `System.nanoTime()` for elapsed time and has no Bukkit dependency.

```java
CooldownManager<UUID> cooldowns = CooldownManager.create();
if (cooldowns.tryAcquire(playerId, Duration.ofSeconds(8))) {
    useAbility();
} else {
    showRemaining(cooldowns.remaining(playerId));
}

TokenBucketRateLimiter<UUID> clicks = new TokenBucketRateLimiter<>(3, 1, Duration.ofSeconds(2));
if (clicks.tryAcquire(playerId)) performGuiAction();
```

`tryAcquire` starts a fixed cooldown only when absent or expired. `start` replaces a deadline; `refresh` extends it only when the requested deadline is later. Zero duration permits an action without retaining a key. Managers can remove individual keys, clear all state, and are `AutoCloseable`. Cooldowns lazily discard expired entries and also expose `cleanupExpired`; token buckets expose `cleanupIdle` for applications with high key churn. No per-key timers are created.

The token bucket begins full and refills continuously at the configured number of tokens per period. `remaining` reports whole available tokens and `retryAfter` reports the time until one token is available. Both managers serialize concurrent operations per key and reject operations after close.

`withClock` is intended for deterministic tests and requires a monotonic `LongSupplier`. Durations must be nonnegative for cooldowns (positive for refill periods); values too large to represent in nanoseconds are rejected. The implementation is process-local and does not persist state or coordinate across servers.
