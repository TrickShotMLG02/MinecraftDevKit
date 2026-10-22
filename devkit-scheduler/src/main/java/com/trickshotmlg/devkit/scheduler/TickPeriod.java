package com.trickshotmlg.devkit.scheduler;

/** A positive interval between server-tick executions. */
public record TickPeriod(long ticks) {
    public TickPeriod {
        if (ticks < 1 || ticks > TickDelay.MAX_TICKS) {
            throw new IllegalArgumentException("Tick period must be between 1 and " + TickDelay.MAX_TICKS + ": " + ticks);
        }
    }

    public static TickPeriod of(long ticks) { return new TickPeriod(ticks); }
}
