package com.trickshotmlg.devkit.bukkit.feedback;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackTimingsTest {
    @Test void roundsPositiveDurationsUpToTicks() {
        assertEquals(new FeedbackTimings(1, 20, 0), FeedbackTimings.of(Duration.ofMillis(1), Duration.ofSeconds(1), Duration.ZERO));
    }
    @Test void rejectsNegativeAndOverflowingDurations() {
        assertThrows(IllegalArgumentException.class, () -> FeedbackTimings.of(Duration.ofSeconds(-1), Duration.ZERO, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> FeedbackTimings.of(Duration.ofSeconds(Long.MAX_VALUE), Duration.ZERO, Duration.ZERO));
    }
    @Test void permitsZeroTimings() { assertEquals(new FeedbackTimings(0, 0, 0), FeedbackTimings.of(Duration.ZERO, Duration.ZERO, Duration.ZERO)); }
}
