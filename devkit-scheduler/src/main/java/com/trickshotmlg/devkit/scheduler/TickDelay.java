package com.trickshotmlg.devkit.scheduler;

/** A non-negative server tick delay. A tick is not a fixed wall-clock duration. */
public record TickDelay(long ticks) {
    public static final long MAX_TICKS = Integer.MAX_VALUE;
    public static final TickDelay ZERO = new TickDelay(0);
    public static final TickDelay ONE = new TickDelay(1);

    public TickDelay {
        if (ticks < 0 || ticks > MAX_TICKS) {
            throw new IllegalArgumentException("Tick delay must be between 0 and " + MAX_TICKS + ": " + ticks);
        }
    }

    public static TickDelay of(long ticks) { return new TickDelay(ticks); }
}
