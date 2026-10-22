package com.trickshotmlg.devkit.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleConcurrencyTest {
    record Service(String name) { }

    @Test void closeWaitsForStartupThenStopsTheFullyStartedGraph() throws Exception {
        CountDownLatch factoryEntered = new CountDownLatch(1);
        CountDownLatch allowStartup = new CountDownLatch(1);
        CountDownLatch closeAttempted = new CountDownLatch(1);
        List<String> calls = java.util.Collections.synchronizedList(new ArrayList<>());
        ServiceKey<Service> service = ServiceKey.of("service", Service.class);
        LifecycleManager manager = LifecycleManager.builder().service(service, context -> {
            factoryEntered.countDown();
            if (!allowStartup.await(5, TimeUnit.SECONDS)) throw new AssertionError("startup test timed out");
            calls.add("started");
            return new Service("service");
        }).serviceWithCleanup(ServiceKey.of("second", Service.class), context -> new Service("second"),
                (value, context) -> calls.add("stopped"), service).build();
        AtomicReference<Throwable> startFailure = new AtomicReference<>();
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        Thread starter = new Thread(() -> {
            try { manager.start(); } catch (Throwable error) { startFailure.set(error); }
        });
        Thread closer = new Thread(() -> {
            closeAttempted.countDown();
            try { manager.close(); } catch (Throwable error) { closeFailure.set(error); }
        });

        starter.start();
        assertTrue(factoryEntered.await(5, TimeUnit.SECONDS));
        closer.start();
        assertTrue(closeAttempted.await(5, TimeUnit.SECONDS));
        allowStartup.countDown();
        starter.join(5000);
        closer.join(5000);

        assertFalse(starter.isAlive());
        assertFalse(closer.isAlive());
        assertNull(startFailure.get());
        assertNull(closeFailure.get());
        assertEquals(LifecycleState.STOPPED, manager.state());
        assertEquals(List.of("started", "stopped"), calls);
    }

    @Test void reentrantCloseDuringStartupIsRejectedAndStartupRollsBack() {
        ServiceKey<Service> service = ServiceKey.of("service", Service.class);
        LifecycleManager[] manager = new LifecycleManager[1];
        manager[0] = LifecycleManager.builder().service(service, context -> {
            manager[0].close();
            return new Service("unreachable");
        }).build();

        LifecycleException failure = assertThrows(LifecycleException.class, manager[0]::start);

        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals(LifecycleState.FAILED, manager[0].state());
        assertDoesNotThrow(manager[0]::close);
    }

    @Test void reloadAndCloseAreSerializedWithoutPublishingRunningAfterStop() throws Exception {
        CountDownLatch reloadEntered = new CountDownLatch(1);
        CountDownLatch allowReload = new CountDownLatch(1);
        CountDownLatch closeAttempted = new CountDownLatch(1);
        ServiceKey<Service> service = ServiceKey.of("service", Service.class);
        LifecycleManager manager = LifecycleManager.builder()
                .service(service, context -> new Service("service"))
                .reload(service, context -> {
                    reloadEntered.countDown();
                    if (!allowReload.await(5, TimeUnit.SECONDS)) throw new AssertionError("reload test timed out");
                }).build();
        manager.start();
        Thread reloader = new Thread(manager::reload);
        Thread closer = new Thread(() -> { closeAttempted.countDown(); manager.close(); });

        reloader.start();
        assertTrue(reloadEntered.await(5, TimeUnit.SECONDS));
        closer.start();
        assertTrue(closeAttempted.await(5, TimeUnit.SECONDS));
        allowReload.countDown();
        reloader.join(5000);
        closer.join(5000);

        assertFalse(reloader.isAlive());
        assertFalse(closer.isAlive());
        assertEquals(LifecycleState.STOPPED, manager.state());
    }
}
