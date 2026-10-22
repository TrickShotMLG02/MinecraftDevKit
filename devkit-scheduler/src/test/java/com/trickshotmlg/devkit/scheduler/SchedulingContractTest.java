package com.trickshotmlg.devkit.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchedulingContractTest {
    @Test
    void tickUnitsHaveExplicitBoundsAndDifferentDelayPeriodRules() {
        assertEquals(0, TickDelay.ZERO.ticks());
        assertEquals(1, TickPeriod.of(1).ticks());
        assertEquals(TickDelay.MAX_TICKS, TickDelay.of(TickDelay.MAX_TICKS).ticks());
        assertThrows(IllegalArgumentException.class, () -> TickDelay.of(-1));
        assertThrows(IllegalArgumentException.class, () -> TickDelay.of(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> TickPeriod.of(0));
        assertThrows(IllegalArgumentException.class, () -> TickPeriod.of(-1));
        assertThrows(IllegalArgumentException.class, () -> TickPeriod.of(TickDelay.MAX_TICKS + 1));
    }

    @Test
    void scheduleValuesAreImmutableAndRequireValidTiming() {
        assertEquals(new TaskSchedule.Once(TickDelay.ZERO), TaskSchedule.immediate());
        assertEquals(new TaskSchedule.Once(TickDelay.ONE), TaskSchedule.nextTick());
        assertEquals(new TaskSchedule.Repeating(TickDelay.of(2), TickPeriod.of(3)),
                TaskSchedule.repeat(TickDelay.of(2), TickPeriod.of(3)));
        assertThrows(NullPointerException.class, () -> new TaskSchedule.Once(null));
        assertThrows(NullPointerException.class, () -> new TaskSchedule.Repeating(TickDelay.ZERO, null));
    }

    @Test
    void onlyTerminalStatesAreReportedTerminal() {
        assertFalse(TaskState.PENDING.terminal());
        assertFalse(TaskState.RUNNING.terminal());
        assertFalse(TaskState.ACTIVE.terminal());
        assertTrue(TaskState.COMPLETED.terminal());
        assertTrue(TaskState.CANCELLED.terminal());
        assertTrue(TaskState.FAILED.terminal());
    }
}
