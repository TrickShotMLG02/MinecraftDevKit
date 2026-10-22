package com.trickshotmlg.devkit.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AsyncTaskResultTest {
    @Test void asyncResultHandsOffWithoutRunningSyncContinuationInline() {
        ManualBackend backend = new ManualBackend();
        TaskScheduler scheduler = TaskSchedulers.create(backend, failure -> fail(failure.cause()));
        AtomicReference<String> shown = new AtomicReference<>();
        List<Throwable> failures = new ArrayList<>();
        AsyncTaskResult<String> result = scheduler.async().submit(() -> "loaded")
                .thenSync(shown::set)
                .onFailure(failures::add);

        backend.run(0);
        assertEquals("loaded", result.result().toCompletableFuture().join());
        assertNull(shown.get());
        backend.run(1);

        assertEquals("loaded", shown.get());
        assertTrue(failures.isEmpty());
        scheduler.close();
    }

    @Test void scopeClosureCancelsPendingResultAndSuppressesLateContinuation() {
        ManualBackend backend = new ManualBackend();
        TaskScheduler scheduler = TaskSchedulers.create(backend, failure -> fail(failure.cause()));
        TaskScope scope = scheduler.scope();
        AtomicReference<String> shown = new AtomicReference<>();
        AsyncTaskResult<String> result = scope.async().submit(() -> "late").thenSync(shown::set);

        scope.close();
        assertThrows(Exception.class, () -> result.result().toCompletableFuture().join());
        backend.run(0);
        assertNull(shown.get());
        assertTrue(result.isCancelled());
        scheduler.close();
    }

    @Test void computationFailureSettlesResultAndNotifiesObserverOnce() {
        ManualBackend backend = new ManualBackend();
        TaskScheduler scheduler = TaskSchedulers.create(backend, failure -> { });
        List<Throwable> observed = new ArrayList<>();
        IllegalArgumentException expected = new IllegalArgumentException("bad input");
        AsyncTaskResult<String> result = scheduler.async().<String>submit(() -> { throw expected; })
                .onFailure(observed::add);

        backend.run(0);
        assertEquals(1, observed.size());
        assertSame(expected, observed.get(0));
        assertThrows(Exception.class, () -> result.result().toCompletableFuture().join());
        assertSame(expected, result.task().failure());
        scheduler.close();
    }

    @Test void explicitCancellationSettlesResultAndIsIdempotent() {
        ManualBackend backend = new ManualBackend();
        TaskScheduler scheduler = TaskSchedulers.create(backend, failure -> fail(failure.cause()));
        AsyncTaskResult<Integer> result = scheduler.async().submit(() -> 42);

        assertTrue(result.cancel());
        assertFalse(result.cancel());
        assertTrue(result.result().toCompletableFuture().isCompletedExceptionally());
        assertThrows(Exception.class, () -> result.result().toCompletableFuture().join());
        Throwable failure = result.task().completion().toCompletableFuture().handle((v, e) -> e).join();
        assertInstanceOf(java.util.concurrent.CompletionException.class, failure);
        assertInstanceOf(CancellationException.class, failure.getCause());
        backend.run(0);
        scheduler.close();
    }

    @Test void cancellingAfterComputationStopsQueuedServerContinuation() {
        ManualBackend backend = new ManualBackend();
        TaskScheduler scheduler = TaskSchedulers.create(backend, failure -> fail(failure.cause()));
        AtomicReference<String> shown = new AtomicReference<>();
        AsyncTaskResult<String> result = scheduler.async().submit(() -> "ready").thenSync(shown::set);

        backend.run(0);
        assertEquals(2, backend.size());
        assertTrue(result.cancel());
        backend.run(1);
        assertNull(shown.get());
        scheduler.close();
    }

    @Test void onlyOneOwnedServerContinuationMayBeRegistered() {
        ManualBackend backend = new ManualBackend();
        TaskScheduler scheduler = TaskSchedulers.create(backend, failure -> fail(failure.cause()));
        AsyncTaskResult<Integer> result = scheduler.async().submit(() -> 1).thenSync(value -> { });
        assertThrows(IllegalStateException.class, () -> result.thenSync(value -> { }));
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
        synchronized int size() { return entries.size(); }
        private static final class Entry {
            final Runnable callback;
            volatile boolean cancelled;
            Entry(Runnable callback) { this.callback = callback; }
        }
    }
}
