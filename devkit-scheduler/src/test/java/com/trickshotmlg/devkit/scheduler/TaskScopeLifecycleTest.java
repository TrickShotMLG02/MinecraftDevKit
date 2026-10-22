package com.trickshotmlg.devkit.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TaskScopeLifecycleTest {
    @Test void taskTransitionsAndOneShotOwnershipAreCleanedUp() {
        ManualBackend backend = new ManualBackend();
        TaskScheduler scheduler = TaskSchedulers.create(backend, failure -> fail(failure.cause()));
        TaskScope scope = scheduler.scope();
        List<Integer> calls = new ArrayList<>();

        TaskHandle handle = scope.sync().later(TickDelay.of(4), () -> calls.add(1));
        assertEquals(TaskState.PENDING, handle.state());
        assertEquals(1, scope.activeTaskCount());
        backend.run(0);

        assertEquals(List.of(1), calls);
        assertEquals(TaskState.COMPLETED, handle.state());
        assertEquals(0, scope.activeTaskCount());
        assertDoesNotThrow(() -> handle.completion().toCompletableFuture().join());
        scheduler.close();
    }

    @Test void scopeAndParentClosureCancelOwnedTasksAndRejectNewWork() {
        ManualBackend backend = new ManualBackend();
        TaskScheduler scheduler = TaskSchedulers.create(backend, failure -> fail(failure.cause()));
        TaskScope parent = scheduler.scope();
        TaskScope child = parent.childScope();
        TaskHandle repeating = child.sync().repeat(TickDelay.ZERO, TickPeriod.of(1), () -> fail("must be cancelled"));

        parent.close();
        assertTrue(parent.isClosed());
        assertTrue(child.isClosed());
        assertEquals(TaskState.CANCELLED, repeating.state());
        assertEquals(0, child.activeTaskCount());
        assertThrows(IllegalStateException.class, () -> child.async().run(() -> { }));
        assertFalse(repeating.cancel());
        scheduler.close();
    }

    @Test void cancellationFromCallbackStopsLaterRepeatingDispatches() {
        ManualBackend backend = new ManualBackend();
        TaskScheduler scheduler = TaskSchedulers.create(backend, failure -> fail(failure.cause()));
        TaskHandle[] handle = new TaskHandle[1];
        int[] calls = {0};
        handle[0] = scheduler.sync().repeat(TickDelay.ZERO, TickPeriod.of(1), context -> {
            calls[0]++;
            context.cancel();
        });

        backend.run(0);
        backend.run(0);
        assertEquals(1, calls[0]);
        assertEquals(TaskState.CANCELLED, handle[0].state());
        scheduler.close();
    }

    @Test void concurrentScopeCloseCannotLetARegisteredTaskEscape() throws Exception {
        CountDownLatch backendEntered = new CountDownLatch(1);
        CountDownLatch allowBackendReturn = new CountDownLatch(1);
        AtomicReference<BackendTask> nativeTask = new AtomicReference<>();
        TaskBackend backend = (target, schedule, callback) -> {
            backendEntered.countDown();
            try {
                if (!allowBackendReturn.await(5, TimeUnit.SECONDS)) throw new AssertionError("test timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            BackendTask created = () -> nativeTask.set(null);
            nativeTask.set(created);
            return created;
        };
        TaskScheduler scheduler = TaskSchedulers.create(backend, failure -> fail(failure.cause()));
        TaskScope scope = scheduler.scope();
        AtomicReference<TaskHandle> handle = new AtomicReference<>();
        Thread scheduling = new Thread(() -> handle.set(scope.sync().run(() -> fail("cancelled task ran"))));
        scheduling.start();
        assertTrue(backendEntered.await(5, TimeUnit.SECONDS));

        scope.close();
        allowBackendReturn.countDown();
        scheduling.join(5000);
        assertFalse(scheduling.isAlive());
        assertNotNull(handle.get());
        assertEquals(TaskState.CANCELLED, handle.get().state());
        assertNull(nativeTask.get(), "native task must be cancelled when attached after scope closure");
        scheduler.close();
    }

    @Test void callbackFailureIsTerminalAndReportedOnce() {
        ManualBackend backend = new ManualBackend();
        List<TaskFailure> failures = new ArrayList<>();
        TaskScheduler scheduler = TaskSchedulers.create(backend, failures::add);
        TaskHandle task = scheduler.sync().repeat(TickDelay.ZERO, TickPeriod.of(2), () -> {
            throw new IllegalStateException("boom");
        });

        backend.run(0);
        backend.run(0);
        assertEquals(TaskState.FAILED, task.state());
        assertEquals(1, failures.size());
        assertSame(task.failure(), failures.get(0).cause());
        assertEquals(0, scheduler.scope().activeTaskCount());
        scheduler.close();
    }

    @Test void continueOnFailureKeepsRepeatingTaskActiveWhenExplicitlyRequested() {
        ManualBackend backend = new ManualBackend();
        List<TaskFailure> failures = new ArrayList<>();
        TaskScheduler scheduler = TaskSchedulers.create(backend, failures::add);
        int[] calls = {0};
        TaskHandle task = scheduler.sync().repeat(TickDelay.ZERO, TickPeriod.of(1),
                TaskFailurePolicy.CONTINUE_ON_FAILURE, context -> {
                    calls[0]++;
                    if (calls[0] == 1) throw new IllegalArgumentException("first attempt");
                });

        backend.run(0);
        assertEquals(TaskState.ACTIVE, task.state());
        assertInstanceOf(IllegalArgumentException.class, task.failure());
        backend.run(0);
        assertEquals(2, calls[0]);
        assertEquals(TaskState.ACTIVE, task.state());
        assertEquals(1, failures.size());
        task.cancel();
        assertEquals(TaskState.CANCELLED, task.state());
        scheduler.close();
    }

    private static final class ManualBackend implements TaskBackend {
        private final List<Entry> entries = new ArrayList<>();
        @Override public synchronized BackendTask schedule(TaskExecutionTarget target, TaskSchedule schedule, Runnable callback) {
            Entry entry = new Entry(callback);
            entries.add(entry);
            return () -> entry.cancelled = true;
        }
        synchronized void run(int index) {
            Entry entry = entries.get(index);
            if (!entry.cancelled) entry.callback.run();
        }
        private static final class Entry {
            final Runnable callback;
            volatile boolean cancelled;
            Entry(Runnable callback) { this.callback = callback; }
        }
    }
}
