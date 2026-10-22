package com.trickshotmlg.devkit.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleStartupTest {
    record Config(String value) { }
    record Database(String configValue) implements AutoCloseable { @Override public void close() { } }

    @Test void startsDependenciesFirstAndPublishesTypedServicesOnlyAfterSuccess() {
        ServiceKey<Database> database = ServiceKey.of("database", Database.class);
        ServiceKey<Config> config = ServiceKey.of("config", Config.class);
        List<String> started = new ArrayList<>();
        LifecycleManager manager = LifecycleManager.builder()
                .service(database, context -> {
                    started.add("database:" + context.require(config).value());
                    return new Database(context.require(config).value());
                }, config)
                .service(config, context -> {
                    started.add("config");
                    return new Config("ready");
                })
                .build();

        assertEquals(LifecycleState.NEW, manager.state());
        assertThrows(IllegalStateException.class, () -> manager.get(config));
        manager.start();

        assertEquals(List.of("config", "database:ready"), started);
        assertEquals(LifecycleState.RUNNING, manager.state());
        assertEquals("ready", manager.get(config).value());
        assertEquals("ready", manager.get(database).configValue());
        assertEquals(ServiceState.RUNNING, manager.serviceStates().get("database"));
        LifecycleException repeatedStart = assertThrows(LifecycleException.class, manager::start);
        assertTrue(repeatedStart.getMessage().contains("Cannot start lifecycle while it is RUNNING"));
        manager.close();
        assertEquals(LifecycleState.STOPPED, manager.state());
        assertThrows(IllegalStateException.class, () -> manager.get(config));
    }

    @Test void contextCannotReadAnUndeclaredService() {
        ServiceKey<Config> config = ServiceKey.of("config", Config.class);
        ServiceKey<Database> database = ServiceKey.of("database", Database.class);
        LifecycleManager manager = LifecycleManager.builder()
                .service(config, context -> new Config("ready"))
                .service(database, context -> {
                    context.require(config);
                    return new Database("ready");
                })
                .build();

        LifecycleException failure = assertThrows(LifecycleException.class, manager::start);
        assertEquals("database", failure.componentName());
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals(LifecycleState.FAILED, manager.state());
        assertEquals(ServiceState.STOPPED, manager.serviceStates().get("config"));
    }

    @Test void nullFactoryResultFailsWithoutPublishingService() {
        ServiceKey<Config> config = ServiceKey.of("config", Config.class);
        LifecycleManager manager = LifecycleManager.builder()
                .service(config, context -> null)
                .build();

        LifecycleException failure = assertThrows(LifecycleException.class, manager::start);
        assertEquals(LifecyclePhase.STARTUP, failure.phase());
        assertEquals("config", failure.componentName());
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals(ServiceState.FAILED, manager.serviceStates().get("config"));
    }

    @Test void reentrantStartIsRejectedAndCausesNormalStartupFailure() {
        ServiceKey<Config> config = ServiceKey.of("config", Config.class);
        LifecycleManager[] manager = new LifecycleManager[1];
        manager[0] = LifecycleManager.builder().service(config, context -> {
            manager[0].start();
            return new Config("unreachable");
        }).build();

        LifecycleException failure = assertThrows(LifecycleException.class, manager[0]::start);
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("Reentrant lifecycle operation"));
        assertEquals(LifecycleState.FAILED, manager[0].state());
    }
}
