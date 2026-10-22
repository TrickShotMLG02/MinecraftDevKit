package com.trickshotmlg.devkit.scheduler;

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
}
