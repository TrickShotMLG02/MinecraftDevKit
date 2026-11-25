package com.trickshotmlg.devkit.bukkit.events;

import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.EventException;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class BukkitEventsTest {
    @Test void registersTypedCallbackWithPriorityAndCancellationPolicyAndClosesOnlyOwnedListener() throws Exception {
        List<Registration> registrations = new ArrayList<>();
        Plugin plugin = plugin(registrations);
        BukkitEvents events = BukkitEvents.create(plugin);
        AtomicInteger received = new AtomicInteger();
        EventSubscription subscription = events.subscribe(TestEvent.class, EventPriority.HIGH, true,
                event -> received.addAndGet(event.value));

        assertEquals(2, registrations.size());
        Registration registered = registrations.get(1);
        assertEquals(TestEvent.class, registered.type);
        assertEquals(EventPriority.HIGH, registered.priority);
        assertTrue(registered.ignoreCancelled);
        registered.executor.execute(registered.listener, new TestEvent(3));
        assertEquals(3, received.get());
        subscription.close();
        subscription.close();
        registered.executor.execute(registered.listener, new TestEvent(4));
        assertEquals(3, received.get());
        assertTrue(subscription.isClosed());
        assertEquals(0, events.subscriptionCount());
        events.close();
    }

    @Test void supportsFiltersMultipleSubscriptionsAndLifecycleClose() throws Exception {
        List<Registration> registrations = new ArrayList<>();
        BukkitEvents events = BukkitEvents.create(plugin(registrations));
        AtomicInteger received = new AtomicInteger();
        EventSubscription filtered = events.subscribe(TestEvent.class, EventPriority.NORMAL, false,
                event -> event.value > 1, event -> received.incrementAndGet());
        events.subscribe(TestEvent.class, event -> received.addAndGet(10));
        registrations.get(1).executor.execute(registrations.get(1).listener, new TestEvent(1));
        registrations.get(2).executor.execute(registrations.get(2).listener, new TestEvent(1));
        registrations.get(1).executor.execute(registrations.get(1).listener, new TestEvent(2));
        assertEquals(11, received.get());
        filtered.close();
        events.close();
        assertEquals(0, events.subscriptionCount());
        assertThrows(IllegalStateException.class, () -> events.subscribe(TestEvent.class, ignored -> { }));
    }

    @Test void callbackFailuresAreReportedAsBukkitEventExceptions() {
        List<Registration> registrations = new ArrayList<>();
        BukkitEvents events = BukkitEvents.create(plugin(registrations));
        events.subscribe(TestEvent.class, ignored -> { throw new IllegalStateException("expected"); });
        EventException failure = assertThrows(EventException.class,
                () -> registrations.get(1).executor.execute(registrations.get(1).listener, new TestEvent(1)));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        events.close();
    }

    @Test void callbackRunsOnDispatchThreadAndRegistrationFailureLeavesNoOwnedSubscription() throws Exception {
        List<Registration> registrations = new ArrayList<>();
        BukkitEvents events = BukkitEvents.create(plugin(registrations));
        AtomicLong callbackThread = new AtomicLong();
        events.subscribe(TestEvent.class, ignored -> callbackThread.set(Thread.currentThread().threadId()));
        Thread dispatch = new Thread(() -> {
            try { registrations.get(1).executor.execute(registrations.get(1).listener, new TestEvent(1)); }
            catch (Exception failure) { throw new RuntimeException(failure); }
        });
        dispatch.start();
        dispatch.join(2000);
        assertFalse(dispatch.isAlive());
        assertEquals(dispatch.threadId(), callbackThread.get());
        events.close();

        PluginManager failingManager = proxy(PluginManager.class, (proxy, method, args) -> {
            if (method.getName().equals("registerEvent") && args[0] == TestEvent.class) {
                throw new IllegalStateException("registration failed");
            }
            return null;
        });
        Server failingServer = proxy(Server.class, (proxy, method, args) -> method.getName().equals("getPluginManager")
                ? failingManager : defaultValue(method.getReturnType()));
        Plugin failingPlugin = proxy(Plugin.class, (proxy, method, args) -> method.getName().equals("getServer")
                ? failingServer : defaultValue(method.getReturnType()));
        BukkitEvents failing = BukkitEvents.create(failingPlugin);
        assertThrows(IllegalStateException.class, () -> failing.subscribe(TestEvent.class, ignored -> { }));
        assertEquals(0, failing.subscriptionCount());
        failing.close();
    }

    @Test void pluginDisableClosesOwnedSubscriptions() throws Exception {
        List<Registration> registrations = new ArrayList<>();
        Plugin plugin = plugin(registrations);
        BukkitEvents events = BukkitEvents.create(plugin);
        EventSubscription subscription = events.subscribe(TestEvent.class, ignored -> { });
        registrations.get(0).executor.execute(registrations.get(0).listener,
                new PluginDisableEvent(plugin));
        assertTrue(subscription.isClosed());
        assertEquals(0, events.subscriptionCount());
    }

    private static Plugin plugin(List<Registration> registrations) {
        PluginManager manager = proxy(PluginManager.class, (proxy, method, args) -> {
            if (method.getName().equals("registerEvent")) {
                registrations.add(new Registration((Class<?>) args[0], (org.bukkit.event.Listener) args[1],
                        (EventPriority) args[2], (EventExecutor) args[3], (Boolean) args[5]));
                return null;
            }
            return defaultValue(method.getReturnType());
        });
        Server server = proxy(Server.class, (proxy, method, args) -> method.getName().equals("getPluginManager")
                ? manager : defaultValue(method.getReturnType()));
        return proxy(Plugin.class, (proxy, method, args) -> method.getName().equals("getServer")
                ? server : method.getName().equals("getName") ? "test" : defaultValue(method.getReturnType()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
    }

    private record Registration(Class<?> type, org.bukkit.event.Listener listener, EventPriority priority,
                                EventExecutor executor, boolean ignoreCancelled) { }

    private static final class TestEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final int value;
        TestEvent(int value) { this.value = value; }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }
}
