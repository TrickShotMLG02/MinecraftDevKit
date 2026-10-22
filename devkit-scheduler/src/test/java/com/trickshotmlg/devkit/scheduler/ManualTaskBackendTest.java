package com.trickshotmlg.devkit.scheduler;

import com.trickshotmlg.devkit.scheduler.testing.ManualTaskBackend;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ManualTaskBackendTest {
    @Test void delayAndStableOrderingUseVirtualTicks() {
        ManualTaskBackend backend = new ManualTaskBackend();
        TaskScheduler scheduler = TaskSchedulers.create(backend, failure -> fail(failure.cause()));
        List<String> calls = new ArrayList<>();
        scheduler.sync().later(TickDelay.of(3), () -> calls.add("first"));
        scheduler.sync().later(TickDelay.of(3), () -> calls.add("second"));

        backend.advanceTicks(2);
        assertEquals(0, backend.runDue(TaskExecutionTarget.SYNC));
        backend.advanceTicks(1);
        assertEquals(2, backend.runDue(TaskExecutionTarget.SYNC));
        assertEquals(List.of("first", "second"), calls);
        scheduler.close();
    }

    @Test void repeatingTasksDoNotCatchUpAndCanCancelTheirOwnSchedule() {
        ManualTaskBackend backend = new ManualTaskBackend();
        TaskScheduler scheduler = TaskSchedulers.create(backend, failure -> fail(failure.cause()));
        AtomicInteger calls = new AtomicInteger();
        TaskHandle task = scheduler.sync().repeat(TickDelay.ZERO, TickPeriod.of(4), context -> {
            if (calls.incrementAndGet() == 2) context.cancel();
        });

        assertEquals(1, backend.runDue(TaskExecutionTarget.SYNC));
        backend.advanceTicks(100);
        assertEquals(1, backend.runDue(TaskExecutionTarget.SYNC));
        assertEquals(2, calls.get());
        assertEquals(TaskState.CANCELLED, task.state());
        assertEquals(0, backend.pendingTaskCount());
        scheduler.close();
    }

    @Test void asyncToSyncPipelineUsesSeparateDeterministicDispatches() {
        ManualTaskBackend backend = new ManualTaskBackend();
        TaskScheduler scheduler = TaskSchedulers.create(backend, failure -> fail(failure.cause()));
        List<String> steps = new ArrayList<>();
        scheduler.async().submit(() -> {
            steps.add("async");
            return "complete";
        }).thenSync(value -> steps.add(value));

        backend.runDue(TaskExecutionTarget.ASYNC);
        assertEquals(List.of("async"), steps);
        backend.runDue(TaskExecutionTarget.SYNC);
        assertEquals(List.of("async", "complete"), steps);
        scheduler.close();
    }
}
