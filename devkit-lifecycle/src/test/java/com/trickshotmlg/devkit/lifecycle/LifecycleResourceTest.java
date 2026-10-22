package com.trickshotmlg.devkit.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleResourceTest {
    record ResourceService(String name, List<String> calls) implements AutoCloseable {
        @Override public void close() { calls.add(name); }
    }
    record PlainService(String name) { }

    @Test void closesServiceThenAcquiredResourcesInReverseOrder() {
        List<String> calls = new ArrayList<>();
        ServiceKey<ResourceService> key = ServiceKey.of("service", ResourceService.class);
        LifecycleManager manager = LifecycleManager.builder().service(key, context -> {
            context.manage(() -> calls.add("first"));
            context.manage(() -> calls.add("second"));
            return new ResourceService("service", calls);
        }).build();
        manager.start();

        manager.close();

        assertEquals(List.of("service", "second", "first"), calls);
        assertEquals(ServiceState.STOPPED, manager.serviceStates().get("service"));
    }

    @Test void returnedServiceAlreadyManagedByItsFactoryIsClosedOnce() {
        List<String> calls = new ArrayList<>();
        ServiceKey<ResourceService> key = ServiceKey.of("service", ResourceService.class);
        LifecycleManager manager = LifecycleManager.builder().service(key, context -> {
            ResourceService service = new ResourceService("service", calls);
            context.manage(service);
            return service;
        }).build();

        manager.start();
        manager.close();

        assertEquals(List.of("service"), calls);
    }

    @Test void explicitServiceCleanupTakesOverAnAlreadyManagedServiceInstance() {
        List<String> calls = new ArrayList<>();
        ServiceKey<ResourceService> key = ServiceKey.of("service", ResourceService.class);
        LifecycleManager manager = LifecycleManager.builder()
                .serviceWithCleanup(key, context -> {
                    ResourceService service = new ResourceService("service.close", calls);
                    context.manage(service);
                    return service;
                }, (service, context) -> calls.add("explicit:" + service.name()))
                .build();

        manager.start();
        manager.close();

        assertEquals(List.of("explicit:service.close"), calls);
    }

    @Test void repeatedSameCleanupFailureDoesNotAbortRemainingShutdown() {
        List<String> calls = new ArrayList<>();
        IllegalStateException sharedFailure = new IllegalStateException("shared cleanup failure");
        ServiceKey<ResourceService> dependency = ServiceKey.of("dependency", ResourceService.class);
        ServiceKey<ResourceService> service = ServiceKey.of("service", ResourceService.class);
        LifecycleManager manager = LifecycleManager.builder()
                .service(dependency, context -> new ResourceService("dependency", calls))
                .service(service, context -> {
                    context.manage(() -> { calls.add("first"); throw sharedFailure; });
                    context.manage(() -> { calls.add("second"); throw sharedFailure; });
                    return new ResourceService("service", calls);
                }, dependency)
                .build();

        manager.start();
        LifecycleException failure = assertThrows(LifecycleException.class, manager::close);

        assertEquals(List.of("service", "second", "first", "dependency"), calls);
        assertEquals(LifecycleState.STOPPED, manager.state());
        assertEquals(LifecyclePhase.SHUTDOWN, failure.phase());
        assertSame(sharedFailure, failure.getCause().getCause());
        assertEquals(0, sharedFailure.getSuppressed().length);
        assertDoesNotThrow(manager::close);
    }

    @Test void failedFactoryClosesItsPartialResourcesThenRollsBackStartedDependencies() {
        List<String> calls = new ArrayList<>();
        ServiceKey<ResourceService> database = ServiceKey.of("database", ResourceService.class);
        ServiceKey<PlainService> feature = ServiceKey.of("feature", PlainService.class);
        IllegalStateException root = new IllegalStateException("startup failed");
        LifecycleManager manager = LifecycleManager.builder()
                .service(database, context -> new ResourceService("database", calls))
                .service(feature, context -> {
                    context.manage(() -> calls.add("partial-a"));
                    context.manage(() -> calls.add("partial-b"));
                    throw root;
                }, database)
                .build();

        LifecycleException failure = assertThrows(LifecycleException.class, manager::start);

        assertSame(root, failure.getCause());
        assertEquals("feature", failure.componentName());
        assertEquals(List.of("partial-b", "partial-a", "database"), calls);
        assertEquals(LifecycleState.FAILED, manager.state());
        assertEquals(ServiceState.FAILED, manager.serviceStates().get("feature"));
        assertEquals(ServiceState.STOPPED, manager.serviceStates().get("database"));
        assertThrows(IllegalStateException.class, () -> manager.get(database));
    }

    @Test void cleanupFailuresDoNotPreventRemainingResourcesOrDependenciesFromClosing() {
        List<String> calls = new ArrayList<>();
        ServiceKey<ResourceService> dependency = ServiceKey.of("dependency", ResourceService.class);
        ServiceKey<ResourceService> feature = ServiceKey.of("feature", ResourceService.class);
        LifecycleManager manager = LifecycleManager.builder()
                .service(dependency, context -> new ResourceService("dependency", calls))
                .service(feature, context -> {
                    context.manage(() -> { calls.add("resource-a"); throw new IllegalStateException("a failed"); });
                    context.manage(() -> calls.add("resource-b"));
                    return new ResourceService("feature", calls);
                }, dependency)
                .build();
        manager.start();

        LifecycleException failure = assertThrows(LifecycleException.class, manager::close);

        assertEquals(LifecyclePhase.SHUTDOWN, failure.phase());
        assertEquals(List.of("feature", "resource-b", "resource-a", "dependency"), calls);
        assertEquals(LifecycleState.STOPPED, manager.state());
        assertEquals(ServiceState.FAILED, manager.serviceStates().get("feature"));
        assertEquals(ServiceState.STOPPED, manager.serviceStates().get("dependency"));
        assertDoesNotThrow(manager::close);
    }

    @Test void duplicateResourceOwnershipFailsStartupAndClosesTheResourceOnce() {
        List<String> calls = new ArrayList<>();
        AutoCloseable shared = () -> calls.add("closed");
        ServiceKey<PlainService> key = ServiceKey.of("service", PlainService.class);
        LifecycleManager manager = LifecycleManager.builder().service(key, context -> {
            context.manage(shared);
            context.manage(shared);
            return new PlainService("unused");
        }).build();

        LifecycleException failure = assertThrows(LifecycleException.class, manager::start);

        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals(List.of("closed"), calls);
    }

    @Test void explicitCleanupSupportsServicesWithoutAutoCloseable() {
        List<String> calls = new ArrayList<>();
        ServiceKey<PlainService> key = ServiceKey.of("plain", PlainService.class);
        LifecycleManager manager = LifecycleManager.builder()
                .serviceWithCleanup(key, context -> new PlainService("value"), (service, context) -> calls.add(service.name()))
                .build();
        manager.start();
        manager.close();
        assertEquals(List.of("value"), calls);
    }

    @Test void closesAutoCloseableFactoryResultWhenRuntimeTypeValidationFails() {
        List<String> calls = new ArrayList<>();
        ServiceKey<PlainService> key = ServiceKey.of("plain", PlainService.class);
        AutoCloseable incompatible = () -> calls.add("closed-incompatible-result");
        @SuppressWarnings({"rawtypes", "unchecked"})
        LifecycleManager manager = LifecycleManager.builder()
                .service((ServiceKey) key, context -> incompatible)
                .build();

        LifecycleException failure = assertThrows(LifecycleException.class, manager::start);

        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains(PlainService.class.getName()));
        assertEquals(List.of("closed-incompatible-result"), calls);
        assertEquals(LifecycleState.FAILED, manager.state());
    }
}
