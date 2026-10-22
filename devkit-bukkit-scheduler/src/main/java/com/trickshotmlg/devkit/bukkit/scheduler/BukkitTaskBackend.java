package com.trickshotmlg.devkit.bukkit.scheduler;

import com.trickshotmlg.devkit.scheduler.BackendTask;
import com.trickshotmlg.devkit.scheduler.TaskBackend;
import com.trickshotmlg.devkit.scheduler.TaskExecutionTarget;
import com.trickshotmlg.devkit.scheduler.TaskSchedule;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

/** Maps shared schedules to Bukkit's native plugin-owned scheduler tasks. */
final class BukkitTaskBackend implements TaskBackend {
    private final Plugin plugin;

    BukkitTaskBackend(Plugin plugin) { this.plugin = Objects.requireNonNull(plugin, "plugin"); }

    @Override public BackendTask schedule(TaskExecutionTarget target, TaskSchedule schedule, Runnable callback) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(callback, "callback");
        if (!plugin.isEnabled()) throw new IllegalStateException("Cannot schedule tasks for a disabled plugin");
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        BukkitTask task;
        if (schedule instanceof TaskSchedule.Once once) {
            long ticks = once.delay().ticks();
            task = target == TaskExecutionTarget.SYNC
                    ? (ticks == 0 ? scheduler.runTask(plugin, callback)
                                  : scheduler.runTaskLater(plugin, callback, (int) ticks))
                    : (ticks == 0 ? scheduler.runTaskAsynchronously(plugin, callback)
                                  : scheduler.runTaskLaterAsynchronously(plugin, callback, (int) ticks));
        } else if (schedule instanceof TaskSchedule.Repeating repeating) {
            task = target == TaskExecutionTarget.SYNC
                    ? scheduler.runTaskTimer(plugin, callback, (int) repeating.initialDelay().ticks(),
                            (int) repeating.period().ticks())
                    : scheduler.runTaskTimerAsynchronously(plugin, callback, (int) repeating.initialDelay().ticks(),
                            (int) repeating.period().ticks());
        } else {
            throw new IllegalArgumentException("Unsupported task schedule: " + schedule.getClass().getName());
        }
        return task::cancel;
    }
}
