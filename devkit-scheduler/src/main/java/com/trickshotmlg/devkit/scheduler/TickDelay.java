package com.trickshotmlg.devkit.scheduler;

import java.time.Duration;

/** A non-negative server tick delay. A tick is not a fixed wall-clock duration.
 * @param ticks number of server ticks to wait, from zero through {@link #MAX_TICKS}
 */
public record TickDelay(long ticks) {
    /** Largest delay representable by the initial Bukkit adapter. */
    public static final long MAX_TICKS = Integer.MAX_VALUE;
    /** Zero-tick delay, dispatched as soon as the backend permits. */
    public static final TickDelay ZERO = new TickDelay(0);
    /** One-tick delay. */
    public static final TickDelay ONE = new TickDelay(1);

    /** Validates the supported tick range.
     * @param ticks number of ticks
     */
    public TickDelay {
        if (ticks < 0 || ticks > MAX_TICKS) {
            throw new IllegalArgumentException("Tick delay must be between 0 and " + MAX_TICKS + ": " + ticks);
        }
    }

    /**
     * Creates a validated tick delay.
     * @param ticks number of server ticks
     * @return tick delay
     */
    public static TickDelay of(long ticks) { return new TickDelay(ticks); }

    /**
     * Converts wall-clock duration to server ticks using ceiling rounding at 20 ticks/second.
     * Zero remains zero; every positive duration becomes at least one tick. Server lag can make
     * elapsed wall time longer than the requested duration.
     * @param duration requested delay
     * @return validated tick delay
     */
    public static TickDelay of(Duration duration) {
        long ticks = durationTicks(duration, true);
        return new TickDelay(ticks);
    }

    static long durationTicks(Duration duration, boolean allowZero) {
        if (duration == null) throw new NullPointerException("duration");
        if (duration.isNegative() || (!allowZero && duration.isZero())) {
            throw new IllegalArgumentException("Duration must be " + (allowZero ? "non-negative" : "positive"));
        }
        if (duration.isZero()) return 0;
        final long nanos;
        try { nanos = duration.toNanos(); }
        catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Duration exceeds the supported tick range", overflow);
        }
        long ticks = nanos / 50_000_000L + (nanos % 50_000_000L == 0 ? 0 : 1);
        if (ticks > MAX_TICKS) throw new IllegalArgumentException("Duration exceeds the supported tick range");
        return ticks;
    }
}
