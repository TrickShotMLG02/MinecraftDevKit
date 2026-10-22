package com.trickshotmlg.devkit.bukkit.scheduler;

import com.trickshotmlg.devkit.scheduler.TaskScheduler;
import com.trickshotmlg.devkit.scheduler.TaskScope;
import com.trickshotmlg.devkit.scheduler.TaskSchedulers;
import com.trickshotmlg.devkit.scheduler.SyncTaskRunner;
import com.trickshotmlg.devkit.scheduler.AsyncTaskRunner;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Bukkit/Paper scheduler bound to one plugin's lifecycle.
 * Synchronous callbacks use Bukkit's traditional primary server thread; async callbacks must not
 * access Bukkit world, entity, player, or inventory state unless the specific API documents it safe.
 * This adapter does not support Folia's region/entity ownership model.
 */
public final class BukkitTaskScheduler implements TaskScheduler {
    private final TaskScheduler delegate;

    private BukkitTaskScheduler(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.delegate = TaskSchedulers.create(new BukkitTaskBackend(plugin), failure ->
                plugin.getLogger().log(Level.SEVERE,
                        "Scheduled task " + failure.task().id() + " failed on " + failure.target()
                                + " execution " + failure.executionCount(), failure.cause()));
    }

    BukkitTaskScheduler(TaskScheduler delegate) { this.delegate = Objects.requireNonNull(delegate, "delegate"); }

    /** Creates a scheduler whose tasks are owned by and cancelled with {@code plugin}. */
    public static BukkitTaskScheduler create(Plugin plugin) {
        return new BukkitTaskScheduler(plugin);
    }

    @Override public SyncTaskRunner sync() { return delegate.sync(); }
    @Override public AsyncTaskRunner async() { return delegate.async(); }
    @Override public TaskScope scope() { return delegate.scope(); }
    @Override public boolean isClosed() { return delegate.isClosed(); }

    /** Cancels tasks created through this scheduler. Tasks scheduled directly through Bukkit are unaffected. */
    @Override public void close() { delegate.close(); }
}
