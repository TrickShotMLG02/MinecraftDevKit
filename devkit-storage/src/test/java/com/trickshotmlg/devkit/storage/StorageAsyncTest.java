package com.trickshotmlg.devkit.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StorageAsyncTest {
    @TempDir Path temporary;

    @Test void asyncQueryRunsOnWorkerAndReturnsTypedValue() throws Exception {
        try (Storage storage = Storage.sqlite(temporary.resolve("async.db"))) {
            storage.update("CREATE TABLE data(value TEXT)", SqlParameters.none());
            storage.update("INSERT INTO data VALUES ('ready')", SqlParameters.none());
            String caller = Thread.currentThread().getName();
            StorageTask<Optional<String>> task = storage.async().queryOne("SELECT value FROM data", SqlParameters.none(), row -> {
                assertNotEquals(caller, Thread.currentThread().getName());
                return row.getString(1);
            });
            assertEquals("ready", task.result().toCompletableFuture().get(3, TimeUnit.SECONDS).orElseThrow());
            assertEquals(StorageTask.State.COMPLETED, task.state());
        }
    }

    @Test void queuedCancellationPreventsWorkAndRunningCancellationSuppressesLateResult() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (Storage storage = Storage.builder().sqliteMemory().asyncThreads(1).asyncQueueCapacity(2).build()) {
            StorageTask<String> running = storage.async().submit(() -> {
                entered.countDown();
                release.await();
                return "late";
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            CountDownLatch shouldNotRun = new CountDownLatch(1);
            StorageTask<Void> queued = storage.async().submit(() -> { shouldNotRun.countDown(); return null; });
            assertTrue(queued.cancel());
            assertTrue(queued.cancel());
            assertEquals(StorageTask.State.CANCELLED, queued.state());
            assertCancellation(queued);

            assertTrue(running.cancel());
            release.countDown();
            assertCancellation(running);
            assertEquals(StorageTask.State.CANCELLED, running.state());
            assertEquals(1, shouldNotRun.getCount());
        } finally { release.countDown(); }
    }

    private static void assertCancellation(StorageTask<?> task) {
        CompletionException failure = assertThrows(CompletionException.class,
                () -> task.result().toCompletableFuture().join());
        assertInstanceOf(CancellationException.class, failure.getCause());
    }

    @Test void boundedQueueRejectsInsteadOfRunningSqlOnCaller() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (Storage storage = Storage.builder().sqliteMemory().asyncThreads(1).asyncQueueCapacity(1).build()) {
            StorageTask<Void> blocker = storage.async().submit(() -> { entered.countDown(); release.await(); return null; });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            StorageTask<Void> queued = storage.async().submit(() -> null);
            String caller = Thread.currentThread().getName();
            StorageTask<String> rejected = storage.async().submit(() -> Thread.currentThread().getName());
            assertEquals(StorageTask.State.FAILED, rejected.state());
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> rejected.result().toCompletableFuture().get());
            assertTrue(failure.getCause() instanceof StorageException);
            assertEquals(caller, Thread.currentThread().getName());
            release.countDown();
            blocker.result().toCompletableFuture().get(3, TimeUnit.SECONDS);
            queued.result().toCompletableFuture().get(3, TimeUnit.SECONDS);
        } finally { release.countDown(); }
    }

    @Test void closeCancelsQueuedAndRunningTasksAndRejectsNewWork() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Storage storage = Storage.builder().sqliteMemory().asyncThreads(1).asyncQueueCapacity(2).build();
        StorageTask<Void> running = storage.async().submit(() -> { entered.countDown(); release.await(); return null; });
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        StorageTask<String> queued = storage.async().submit(() -> "never");
        storage.close();
        assertEquals(StorageTask.State.CANCELLED, running.state());
        assertEquals(StorageTask.State.CANCELLED, queued.state());
        assertThrows(StorageClosedException.class, () -> storage.async().submit(() -> "rejected"));
        release.countDown();
    }

    @Test void externalExecutorIsNotClosedAndInlineExecutorCannotRunDatabaseWork() throws Exception {
        ThreadPoolExecutor external = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4));
        Storage storage = Storage.builder().sqliteMemory().executor(external).build();
        storage.close();
        assertFalse(external.isShutdown());
        external.shutdownNow();

        try (Storage inline = Storage.builder().sqliteMemory().executor(Runnable::run).build()) {
            StorageTask<String> rejected = inline.async().submit(() -> "must not run inline");
            assertEquals(StorageTask.State.FAILED, rejected.state());
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> rejected.result().toCompletableFuture().get());
            assertTrue(failure.getCause() instanceof IllegalStateException);
        }
    }
}
