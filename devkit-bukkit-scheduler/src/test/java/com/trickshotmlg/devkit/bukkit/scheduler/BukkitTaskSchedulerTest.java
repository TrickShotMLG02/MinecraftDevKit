package com.trickshotmlg.devkit.bukkit.scheduler;

import com.trickshotmlg.devkit.scheduler.TaskHandle;
import com.trickshotmlg.devkit.scheduler.TaskSchedulers;
import com.trickshotmlg.devkit.scheduler.TaskState;
import com.trickshotmlg.devkit.scheduler.TickDelay;
import com.trickshotmlg.devkit.scheduler.TickPeriod;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class BukkitTaskSchedulerTest {
    @Test void routesSyncAndAsyncSchedulesToNativeMethodsAndCancelsNativeTask() {
        List<Call> calls = new ArrayList<>();
        AtomicInteger cancellations = new AtomicInteger();
        BukkitTask nativeTask = proxy(BukkitTask.class, (proxy, method, args) -> {
            if (method.getName().equals("cancel")) { cancellations.incrementAndGet(); return null; }
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            return null;
        });
        BukkitScheduler nativeScheduler = proxy(BukkitScheduler.class, (proxy, method, args) -> {
            if (method.getName().startsWith("runTask")) {
                calls.add(new Call(method.getName(), args == null ? new Object[0] : args.clone()));
                return nativeTask;
            }
            return null;
        });
        Plugin plugin = plugin(nativeScheduler, new AtomicBoolean(true));
        BukkitTaskScheduler scheduler = BukkitTaskScheduler.create(plugin);

        TaskHandle sync = scheduler.sync().nextTick(() -> { });
        TaskHandle async = scheduler.async().repeat(TickDelay.of(3), TickPeriod.of(7), () -> { });

        assertEquals("runTaskLater", calls.get(0).method());
        assertEquals(1L, calls.get(0).arguments()[2]);
        assertEquals("runTaskTimerAsynchronously", calls.get(1).method());
        assertEquals(3L, calls.get(1).arguments()[2]);
        assertEquals(7L, calls.get(1).arguments()[3]);
        scheduler.close();
        assertEquals(TaskState.CANCELLED, sync.state());
        assertEquals(TaskState.CANCELLED, async.state());
        assertEquals(2, cancellations.get());
    }

    @Test void rejectsSchedulingAfterPluginIsDisabledWithoutLosingFailureState() {
        AtomicBoolean enabled = new AtomicBoolean(false);
        List<Throwable> failures = new ArrayList<>();
        Plugin plugin = plugin(proxy(BukkitScheduler.class, (proxy, method, args) -> null), enabled);
        BukkitTaskScheduler scheduler = new BukkitTaskScheduler(TaskSchedulers.create(
                new BukkitTaskBackend(new java.util.concurrent.atomic.AtomicReference<>(plugin)),
                failure -> failures.add(failure.cause())));

        TaskHandle task = scheduler.sync().run(() -> fail("disabled plugin task ran"));
        assertEquals(TaskState.FAILED, task.state());
        assertEquals(1, failures.size());
        assertInstanceOf(IllegalStateException.class, failures.get(0));
        scheduler.close();
    }

    @Test void pluginDisableClosesSchedulerAndReleasesOwnedTasks() {
        BukkitTask nativeTask = proxy(BukkitTask.class, (proxy, method, args) -> null);
        BukkitScheduler nativeScheduler = proxy(BukkitScheduler.class, (proxy, method, args) -> nativeTask);
        Plugin plugin = plugin(nativeScheduler, new AtomicBoolean(true));
        BukkitTaskScheduler scheduler = BukkitTaskScheduler.create(plugin);
        TaskHandle pending = scheduler.sync().later(TickDelay.of(50), () -> fail("disabled plugin task ran"));

        scheduler.closeForPlugin(plugin);
        assertTrue(scheduler.isClosed());
        assertEquals(TaskState.CANCELLED, pending.state());
        assertThrows(IllegalStateException.class, () -> scheduler.sync().run(() -> { }));
        assertDoesNotThrow(scheduler::close);
    }

    private static Plugin plugin(BukkitScheduler scheduler, AtomicBoolean enabled) {
        PluginManager manager = proxy(PluginManager.class, (proxy, method, args) -> null);
        Server server = proxy(Server.class, (proxy, method, args) ->
                method.getName().equals("getScheduler") ? scheduler
                        : method.getName().equals("getPluginManager") ? manager : null);
        return proxy(Plugin.class, (proxy, method, args) -> switch (method.getName()) {
            case "getServer" -> server;
            case "getLogger" -> Logger.getLogger("scheduler-test");
            case "isEnabled" -> enabled.get();
            case "getName" -> "scheduler-test";
            default -> null;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private record Call(String method, Object[] arguments) { }

}
