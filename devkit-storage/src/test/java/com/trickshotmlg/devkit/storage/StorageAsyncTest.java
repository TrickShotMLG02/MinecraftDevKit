package com.trickshotmlg.devkit.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executor;

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

    @Test void closeDoesNotRunCompletionCallbacksWhileHoldingLifecycleLock() throws Exception {
        Storage storage = Storage.builder().sqliteMemory().asyncThreads(1).asyncQueueCapacity(1).build();
        StorageAsync async = storage.async();
        CountDownLatch workEntered = new CountDownLatch(1);
        CountDownLatch releaseWork = new CountDownLatch(1);
        StorageTask<Void> task = async.submit(() -> {
            workEntered.countDown();
            releaseWork.await();
            return null;
        });
        assertTrue(workEntered.await(3, TimeUnit.SECONDS));
        CountDownLatch callbackEntered = new CountDownLatch(1);
        AtomicBoolean submissionBlockedByCloseLock = new AtomicBoolean();
        task.result().whenComplete((ignored, failure) -> {
            callbackEntered.countDown();
            CountDownLatch attemptingSubmission = new CountDownLatch(1);
            CountDownLatch submissionFinished = new CountDownLatch(1);
            Thread submitter = new Thread(() -> {
                attemptingSubmission.countDown();
                try { async.submit(() -> null); }
                catch (StorageClosedException expected) { /* close already linearized */ }
                finally { submissionFinished.countDown(); }
            }, "storage-close-callback-probe");
            submitter.start();
            try {
                if (!attemptingSubmission.await(3, TimeUnit.SECONDS)) return;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (submissionFinished.getCount() != 0 && System.nanoTime() < deadline) {
                    if (submitter.getState() == Thread.State.BLOCKED) {
                        submissionBlockedByCloseLock.set(true);
                        break;
                    }
                    Thread.onSpinWait();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });

        try { storage.close(); }
        finally { releaseWork.countDown(); }
        assertTrue(callbackEntered.await(3, TimeUnit.SECONDS));
        assertFalse(submissionBlockedByCloseLock.get(),
                "future completion callbacks must not run while the async lifecycle lock is held");
        assertEquals(StorageTask.State.CANCELLED, task.state());
    }

    @Test void acceptedSubmissionRacingCloseIsSettledAndCannotRunAfterward() throws Exception {
        CountDownLatch executeEntered = new CountDownLatch(1);
        CountDownLatch admit = new CountDownLatch(1);
        AtomicReference<Runnable> queuedOperation = new AtomicReference<>();
        Executor controlled = command -> {
            executeEntered.countDown();
            try {
                if (!admit.await(3, TimeUnit.SECONDS)) throw new RejectedExecutionException("test admission timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException(interrupted);
            }
            queuedOperation.set(command);
        };
        Storage storage = Storage.builder().sqliteMemory().executor(controlled).build();
        StorageAsync async = storage.async();
        AtomicReference<StorageTask<Boolean>> submitted = new AtomicReference<>();
        AtomicReference<Throwable> submitFailure = new AtomicReference<>();
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        AtomicBoolean workRan = new AtomicBoolean();
        Thread submitter = new Thread(() -> {
            try { submitted.set(async.submit(() -> { workRan.set(true); return true; })); }
            catch (Throwable failure) { submitFailure.set(failure); }
        }, "storage-submit-race");
        submitter.start();
        assertTrue(executeEntered.await(3, TimeUnit.SECONDS));

        CountDownLatch closeStarted = new CountDownLatch(1);
        Thread closer = new Thread(() -> {
            closeStarted.countDown();
            try { storage.close(); }
            catch (Throwable failure) { closeFailure.set(failure); }
        }, "storage-close-race");
        closer.start();
        assertTrue(closeStarted.await(3, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (closer.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.onSpinWait();
        assertEquals(Thread.State.BLOCKED, closer.getState(), "close should wait for the in-flight admission decision");

        admit.countDown();
        submitter.join(3000);
        closer.join(3000);
        assertFalse(submitter.isAlive());
        assertFalse(closer.isAlive());
        assertNull(submitFailure.get());
        assertNull(closeFailure.get());
        StorageTask<Boolean> task = submitted.get();
        assertNotNull(task);
        assertEquals(StorageTask.State.CANCELLED, task.state());
        assertCancellation(task);

        queuedOperation.get().run();
        assertFalse(workRan.get(), "work accepted just before close must not start after close cancels it");
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
