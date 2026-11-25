package com.trickshotmlg.devkit.scheduler;

import java.time.Duration;

/** A positive interval between server-tick executions.
 * @param ticks period in server ticks, from one through {@link TickDelay#MAX_TICKS}
 */
public record TickPeriod(long ticks) {
    /** Validates a positive tick period.
     * @param ticks number of ticks
     */
    public TickPeriod {
        if (ticks < 1 || ticks > TickDelay.MAX_TICKS) {
            throw new IllegalArgumentException("Tick period must be between 1 and " + TickDelay.MAX_TICKS + ": " + ticks);
        }
    }

    /**
     * Creates a validated repeating interval.
     * @param ticks number of ticks
     * @return repeating period
     */
    public static TickPeriod of(long ticks) { return new TickPeriod(ticks); }

    /**
     * Converts a positive duration to server ticks using ceiling rounding at 20 ticks/second.
     * @param duration positive requested period
     * @return a period of at least one tick
     */
    public static TickPeriod of(Duration duration) {
        return new TickPeriod(TickDelay.durationTicks(duration, false));
    }
}
