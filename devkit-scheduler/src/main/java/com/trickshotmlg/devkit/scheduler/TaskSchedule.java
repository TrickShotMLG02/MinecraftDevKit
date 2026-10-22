package com.trickshotmlg.devkit.scheduler;

import java.util.Objects;

/** Immutable tick-based schedule accepted by a scheduling backend. */
public sealed interface TaskSchedule permits TaskSchedule.Once, TaskSchedule.Repeating {
    /**
     * Schedule as soon as the backend can dispatch the task. Bukkit sync dispatch is on a later tick.
     * @return immediate one-shot schedule
     */
    static Once immediate() { return new Once(TickDelay.ZERO); }

    /**
     * Schedule after one server tick.
     * @return one-tick one-shot schedule
     */
    static Once nextTick() { return new Once(TickDelay.ONE); }

    /**
     * Schedule once after {@code delay} server ticks.
     * @param delay tick delay
     * @return one-shot schedule
     */
    static Once later(TickDelay delay) { return new Once(delay); }

    /**
     * Schedule repeatedly using a server-tick initial delay and period.
     * @param initialDelay delay before first invocation
     * @param period interval between later invocations
     * @return repeating schedule
     */
    static Repeating repeat(TickDelay initialDelay, TickPeriod period) {
        return new Repeating(initialDelay, period);
    }

    /** One-shot schedule.
     * @param delay ticks before dispatch
     */
    record Once(TickDelay delay) implements TaskSchedule {
        /** Validates the delay.
         * @param delay ticks before dispatch
         */
        public Once { Objects.requireNonNull(delay, "delay"); }
    }

    /** Repeating schedule.
     * @param initialDelay ticks before the first dispatch
     * @param period ticks between later dispatches
     */
    record Repeating(TickDelay initialDelay, TickPeriod period) implements TaskSchedule {
        /** Validates the repeat timing.
         * @param initialDelay ticks before first dispatch
         * @param period ticks between later dispatches
         */
        public Repeating {
            Objects.requireNonNull(initialDelay, "initialDelay");
            Objects.requireNonNull(period, "period");
        }
    }
}
