package com.trickshotmlg.devkit.examples.scheduler;

import com.trickshotmlg.devkit.bukkit.scheduler.BukkitTaskScheduler;
import com.trickshotmlg.devkit.bukkit.scheduler.BukkitPlayerTaskScopes;
import com.trickshotmlg.devkit.scheduler.AsyncTaskResult;
import com.trickshotmlg.devkit.scheduler.TaskHandle;
import com.trickshotmlg.devkit.scheduler.TaskScope;
import com.trickshotmlg.devkit.text.StaticTextProvider;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Standalone Paper plugin demonstrating scoped and asynchronous task scheduling. */
public final class SchedulerExamplePlugin extends JavaPlugin implements Listener {
    private BukkitTaskScheduler scheduler;
    private TaskScope demoScope;
    private BukkitPlayerTaskScopes playerScopes;
    private final Map<UUID, TaskHandle> repeating = new ConcurrentHashMap<>();

    @Override public void onEnable() {
        scheduler = BukkitTaskScheduler.create(this);
        demoScope = scheduler.scope();
        playerScopes = BukkitPlayerTaskScopes.create(this, demoScope, true);
        getServer().getPluginManager().registerEvents(this, this);
        new com.trickshotmlg.devkit.bukkit.commands.BukkitCommandAdapter(
                this, new StaticTextProvider(), java.util.Locale.ENGLISH)
                .register(SchedulerExampleCommands.create(this));
    }

    void demo(Player player) {
        UUID viewerId = player.getUniqueId();
        scheduler.sync().run(() -> sendIfOnline(viewerId, "Immediate sync task ran on the server thread."));
        scheduler.sync().later(java.time.Duration.ofSeconds(2), () -> sendIfOnline(viewerId,
                "Delayed task ran after 40 server ticks (tick time varies with server load)."));
        player.sendMessage(Component.text("Scheduled a next-tick message and a 40-tick delayed message."));
    }

    void startRepeating(Player player) {
        UUID id = player.getUniqueId();
        stopRepeating(id);
        AtomicInteger count = new AtomicInteger();
        TaskHandle handle = playerScopes.forPlayer(id).sync().repeat(java.time.Duration.ofSeconds(1),
                java.time.Duration.ofSeconds(1), context -> {
            Player online = Bukkit.getPlayer(id);
            if (online == null || !online.isOnline()) {
                context.cancel();
                repeating.remove(id);
                return;
            }
            online.sendMessage(Component.text("Scoped repeat #" + count.incrementAndGet() + "."));
        });
        repeating.put(id, handle);
        player.sendMessage(Component.text("Started one message per 20 ticks. Use /mdkscheduler cancel <player> to stop it."));
    }

    void stopRepeating(Player player) { stopRepeating(player.getUniqueId()); }

    private void stopRepeating(UUID playerId) {
        TaskHandle old = repeating.remove(playerId);
        if (old != null) old.cancel();
    }

    void runAsyncDemo(Player player) {
        UUID id = player.getUniqueId();
        AsyncTaskResult<Long> result = scheduler.async().submit(() -> {
            long checksum = 0;
            for (int value = 1; value <= 2_000_000; value++) checksum += value;
            return checksum;
        });
        result.thenSync(checksum -> sendIfOnline(id, "Background calculation finished: " + checksum))
                .onFailure(error -> {
                    if (!(error instanceof java.util.concurrent.CancellationException)) {
                        getLogger().log(java.util.logging.Level.SEVERE, "Async scheduler example failed", error);
                    }
                });
        player.sendMessage(Component.text("Started the background calculation."));
    }

    void sendStatus(com.trickshotmlg.devkit.commands.CommandSender sender) {
        sender.sendMessage(Component.text("Scheduler closed=" + scheduler.isClosed()
                + ", active scoped tasks=" + demoScope.activeTaskCount()
                + ", repeating player tasks=" + repeating.size()));
    }

    private void sendIfOnline(UUID playerId, String message) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null && online.isOnline()) online.sendMessage(Component.text(message));
    }

    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) {
        stopRepeating(event.getPlayer().getUniqueId());
    }

    @Override public void onDisable() {
        repeating.clear();
        if (playerScopes != null) playerScopes.close();
        if (demoScope != null) demoScope.close();
        if (scheduler != null) scheduler.close();
    }
}
