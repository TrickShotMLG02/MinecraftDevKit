package com.trickshotmlg.devkit.bukkit.scheduler;

import com.trickshotmlg.devkit.scheduler.TaskScheduler;
import com.trickshotmlg.devkit.scheduler.TaskScope;
import com.trickshotmlg.devkit.scheduler.TaskSchedulers;
import com.trickshotmlg.devkit.scheduler.SyncTaskRunner;
import com.trickshotmlg.devkit.scheduler.AsyncTaskRunner;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Bukkit/Paper scheduler bound to one plugin's lifecycle.
 * Synchronous callbacks use Bukkit's traditional primary server thread; async callbacks must not
 * access Bukkit world, entity, player, or inventory state unless the specific API documents it safe.
 * This adapter does not support Folia's region/entity ownership model.
 */
public final class BukkitTaskScheduler implements TaskScheduler, Listener {
    private final AtomicReference<Plugin> plugin;
    private final BukkitTaskBackend backend;
    private final TaskScheduler delegate;
    private final AtomicBoolean closed = new AtomicBoolean();

    private BukkitTaskScheduler(Plugin plugin) {
        Plugin owner = Objects.requireNonNull(plugin, "plugin");
        this.plugin = new AtomicReference<>(owner);
        this.backend = new BukkitTaskBackend(this.plugin);
        this.delegate = TaskSchedulers.create(backend, failure -> {
            Plugin activeOwner = this.plugin.get();
            if (activeOwner != null) {
                activeOwner.getLogger().log(Level.SEVERE,
                        "Scheduled task " + failure.task().id() + " failed on " + failure.target()
                                + " execution " + failure.executionCount(), failure.cause());
            } else {
                Thread thread = Thread.currentThread();
                Thread.UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
                if (handler != null) handler.uncaughtException(thread, failure.cause());
            }
        });
    }

    BukkitTaskScheduler(TaskScheduler delegate) {
        this.plugin = new AtomicReference<>();
        this.backend = new BukkitTaskBackend(this.plugin);
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /** Creates a scheduler whose tasks are owned by and cancelled with {@code plugin}. */
    public static BukkitTaskScheduler create(Plugin plugin) {
        BukkitTaskScheduler scheduler = new BukkitTaskScheduler(plugin);
        try {
            plugin.getServer().getPluginManager().registerEvents(scheduler, plugin);
            return scheduler;
        } catch (RuntimeException failure) {
            scheduler.close();
            throw failure;
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        closeForPlugin(event.getPlugin());
    }

    void closeForPlugin(Plugin disablingPlugin) {
        if (disablingPlugin == plugin.get()) close();
    }

    @Override public SyncTaskRunner sync() { return delegate.sync(); }
    @Override public AsyncTaskRunner async() { return delegate.async(); }
    @Override public TaskScope scope() { return delegate.scope(); }
    @Override public boolean isClosed() { return closed.get() || delegate.isClosed(); }

    /** Cancels tasks created through this scheduler. Tasks scheduled directly through Bukkit are unaffected. */
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        delegate.close();
        HandlerList.unregisterAll(this);
        backend.close();
        plugin.set(null);
    }
}
