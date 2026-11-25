package com.trickshotmlg.devkit.scheduler;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DurationScheduleTest {
    @Test void roundsUpToTicksAndPreservesZeroDelay() {
        assertEquals(TickDelay.ZERO, TickDelay.of(Duration.ZERO));
        assertEquals(TickDelay.ONE, TickDelay.of(Duration.ofNanos(1)));
        assertEquals(TickDelay.of(1), TickDelay.of(Duration.ofMillis(50)));
        assertEquals(TickDelay.of(2), TickDelay.of(Duration.ofMillis(51)));
        assertEquals(TickPeriod.of(1), TickPeriod.of(Duration.ofNanos(1)));
    }

    @Test void rejectsNegativeZeroPeriodAndOverflowDurations() {
        assertThrows(IllegalArgumentException.class, () -> TickDelay.of(Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class, () -> TickPeriod.of(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> TickPeriod.of(Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class, () -> TickDelay.of(Duration.ofSeconds(Long.MAX_VALUE)));
        assertThrows(IllegalArgumentException.class, () -> TickDelay.of(Duration.ofDays(100_000_000)));
    }

    @Test void acceptsLargestRepresentableDelayAndRejectsNextDuration() {
        Duration maximum = Duration.ofMillis(TickDelay.MAX_TICKS * 50L);
        assertEquals(TickDelay.MAX_TICKS, TickDelay.of(maximum).ticks());
        assertThrows(IllegalArgumentException.class, () -> TickDelay.of(maximum.plusNanos(1)));
    }
}
