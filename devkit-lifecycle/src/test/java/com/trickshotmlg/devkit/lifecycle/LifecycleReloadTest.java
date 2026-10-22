package com.trickshotmlg.devkit.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleReloadTest {
    record Service(String name) { }

    @Test void reloadRunsOnlyDeclaredHooksInDependencyOrderAndReportsPartialFailure() {
        ServiceKey<Service> config = ServiceKey.of("config", Service.class);
        ServiceKey<Service> localization = ServiceKey.of("localization", Service.class);
        List<String> calls = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("bad catalog");
        LifecycleManager manager = LifecycleManager.builder()
                .service(localization, context -> new Service("localization"), config)
                .service(config, context -> new Service("config"))
                .reload(localization, context -> { calls.add("localization"); throw failure; })
                .reload(config, context -> calls.add("config"))
                .build();
        manager.start();

        LifecycleReloadResult result = manager.reload();

        assertEquals(List.of("config", "localization"), calls);
        assertEquals(List.of("config", "localization"), result.attempted());
        assertFalse(result.successful());
        assertEquals(1, result.failures().size());
        assertEquals("localization", result.failures().get(0).componentName());
        assertSame(failure, result.failures().get(0).getCause());
        assertEquals(LifecycleState.RUNNING, manager.state());
        assertEquals(List.of("localization", "config"), manager.shutdownOrder());
        manager.close();
        assertThrows(LifecycleException.class, manager::reload);
    }

    @Test void diagnosticsAreImmutablePointInTimeSnapshots() {
        ServiceKey<Service> key = ServiceKey.of("service", Service.class);
        LifecycleManager manager = LifecycleManager.builder().service(key, context -> new Service("value")).build();
        LifecycleDiagnostics before = manager.diagnostics();
        manager.start();
        LifecycleDiagnostics running = manager.diagnostics();

        assertEquals(LifecycleState.NEW, before.state());
        assertEquals(LifecycleState.RUNNING, running.state());
        assertEquals(ServiceState.RUNNING, running.components().get("service"));
        assertThrows(UnsupportedOperationException.class,
                () -> running.components().put("other", ServiceState.FAILED));
        manager.close();
    }
}
