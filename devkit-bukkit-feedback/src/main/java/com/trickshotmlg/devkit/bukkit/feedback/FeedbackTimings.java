package com.trickshotmlg.devkit.bukkit.feedback;

import java.time.Duration;
import java.util.Objects;

/** Bukkit title timings, rounded up to server ticks (20 ticks per second). */
public record FeedbackTimings(int fadeInTicks, int stayTicks, int fadeOutTicks) {
    public FeedbackTimings {
        if (fadeInTicks < 0 || stayTicks < 0 || fadeOutTicks < 0) throw new IllegalArgumentException("Title timings cannot be negative");
    }

    public static FeedbackTimings of(Duration fadeIn, Duration stay, Duration fadeOut) {
        return new FeedbackTimings(ticks(fadeIn), ticks(stay), ticks(fadeOut));
    }

    private static int ticks(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) throw new IllegalArgumentException("Title timing cannot be negative");
        try {
            long seconds = duration.getSeconds();
            long nanos = duration.getNano();
            long value = Math.addExact(Math.multiplyExact(seconds, 20), (nanos * 20 + 999_999_999L) / 1_000_000_000L);
            if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("Title timing exceeds Bukkit's tick range");
            return (int) value;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Title timing exceeds Bukkit's tick range", exception);
        }
    }
}
