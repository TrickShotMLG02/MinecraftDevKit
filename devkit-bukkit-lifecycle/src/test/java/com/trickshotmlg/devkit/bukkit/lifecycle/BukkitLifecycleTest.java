package com.trickshotmlg.devkit.bukkit.lifecycle;

import com.trickshotmlg.devkit.lifecycle.LifecycleManager;
import com.trickshotmlg.devkit.lifecycle.LifecycleState;
import com.trickshotmlg.devkit.lifecycle.ServiceKey;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class BukkitLifecycleTest {
    record Service() { }

    @Test void delegatesStartupAndShutdownAndIgnoresOtherPluginDisable() {
        ServiceKey<Service> key = ServiceKey.of("service", Service.class);
        LifecycleManager manager = LifecycleManager.builder().service(key, context -> new Service()).build();
        Plugin owner = plugin("owner");
        BukkitLifecycle lifecycle = BukkitLifecycle.forTest(owner, manager, () -> true);

        lifecycle.start();
        assertEquals(LifecycleState.RUNNING, lifecycle.state());
        assertNotNull(manager.get(key));

        lifecycle.closeForPlugin(plugin("other"));
        assertEquals(LifecycleState.RUNNING, lifecycle.state());
        lifecycle.closeForPlugin(owner);
        assertEquals(LifecycleState.STOPPED, lifecycle.state());
        assertDoesNotThrow(lifecycle::close);
    }

    @Test void refusesLifecycleCallbacksOffTheConfiguredServerThread() {
        LifecycleManager manager = LifecycleManager.builder().build();
        BukkitLifecycle lifecycle = BukkitLifecycle.forTest(plugin("owner"), manager, () -> false);

        assertThrows(IllegalStateException.class, lifecycle::start);
        assertEquals(LifecycleState.NEW, manager.state());
        assertThrows(IllegalStateException.class, lifecycle::close);
        assertEquals(LifecycleState.NEW, manager.state());
    }

    @Test void propagatesStartupFailureInsteadOfPretendingPluginIsOperational() {
        ServiceKey<Service> key = ServiceKey.of("service", Service.class);
        LifecycleManager manager = LifecycleManager.builder().service(key, context -> { throw new Exception("failed"); }).build();
        BukkitLifecycle lifecycle = BukkitLifecycle.forTest(plugin("owner"), manager, () -> true);

        assertThrows(com.trickshotmlg.devkit.lifecycle.LifecycleException.class, lifecycle::start);
        assertEquals(LifecycleState.FAILED, lifecycle.state());
        lifecycle.close();
    }

    private static Plugin plugin(String name) {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> method.getName().equals("getLogger") ? Logger.getLogger(name) : null);
    }
}
