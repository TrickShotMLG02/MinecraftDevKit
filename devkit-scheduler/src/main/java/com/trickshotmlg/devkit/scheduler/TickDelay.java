package com.trickshotmlg.devkit.scheduler;

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
}
