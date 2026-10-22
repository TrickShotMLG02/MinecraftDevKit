package com.trickshotmlg.devkit.scheduler;

import java.util.Objects;

/** Immutable tick-based schedule accepted by a scheduling backend. */
public sealed interface TaskSchedule permits TaskSchedule.Once, TaskSchedule.Repeating {
    /** Schedule as soon as the backend can dispatch the task. Bukkit sync dispatch is next tick. */
    static Once immediate() { return new Once(TickDelay.ZERO); }

    static Once nextTick() { return new Once(TickDelay.ONE); }

    static Once later(TickDelay delay) { return new Once(delay); }

    static Repeating repeat(TickDelay initialDelay, TickPeriod period) {
        return new Repeating(initialDelay, period);
    }

    record Once(TickDelay delay) implements TaskSchedule {
        public Once { Objects.requireNonNull(delay, "delay"); }
    }

    record Repeating(TickDelay initialDelay, TickPeriod period) implements TaskSchedule {
        public Repeating {
            Objects.requireNonNull(initialDelay, "initialDelay");
            Objects.requireNonNull(period, "period");
        }
    }
}
