package com.trickshotmlg.devkit.bukkit.feedback;

import com.trickshotmlg.devkit.text.TextProvider;
import com.trickshotmlg.devkit.text.TextRef;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.HandlerList;
import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.ServiceLoader;

/** Common Bukkit feedback transport. Adventure components are serialized to legacy formatting for Spigot compatibility. */
public final class BukkitFeedback implements AutoCloseable, Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final java.util.List<FeedbackPlatformExtension> EXTENSIONS = ServiceLoader
            .load(FeedbackPlatformExtension.class, FeedbackPlatformExtension.class.getClassLoader())
            .stream().map(ServiceLoader.Provider::get).toList();
    private final Plugin plugin;
    private final TextProvider textProvider;
    private final Set<ManagedBossBar> bossBars = new LinkedHashSet<>();
    private boolean closed;

    private BukkitFeedback(Plugin plugin, TextProvider textProvider) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.textProvider = textProvider;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static BukkitFeedback create(Plugin plugin) { return new BukkitFeedback(plugin, null); }
    /** Returns whether an optional platform extension sends native Adventure components. */
    public static boolean supportsNativeAdventure() { return !EXTENSIONS.isEmpty(); }
    public static BukkitFeedback create(Plugin plugin, TextProvider textProvider) {
        return new BukkitFeedback(plugin, Objects.requireNonNull(textProvider, "textProvider"));
    }

    public void message(Player player, Component component) {
        ensureOpen();
        Player target = Objects.requireNonNull(player, "player");
        if (EXTENSIONS.isEmpty()) target.spigot().sendMessage(TextComponent.fromLegacyText(legacy(component)));
        else EXTENSIONS.get(0).message(target, Objects.requireNonNull(component, "component"));
    }

    public void message(Player player, TextRef reference, Map<String, ?> placeholders) {
        message(player, resolve(player, reference, placeholders));
    }
    public void message(Player player, TextRef reference) { message(player, reference, Map.of()); }

    public void actionBar(Player player, Component component) {
        ensureOpen();
        Player target = Objects.requireNonNull(player, "player");
        if (EXTENSIONS.isEmpty()) target.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(legacy(component)));
        else EXTENSIONS.get(0).actionBar(target, Objects.requireNonNull(component, "component"));
    }
    public void actionBar(Player player, TextRef reference, Map<String, ?> placeholders) {
        actionBar(player, resolve(player, reference, placeholders));
    }
    public void actionBar(Player player, TextRef reference) { actionBar(player, reference, Map.of()); }

    public void title(Player player, Component title, Component subtitle, FeedbackTimings timings) {
        ensureOpen();
        Objects.requireNonNull(timings, "timings");
        Player target = Objects.requireNonNull(player, "player");
        if (EXTENSIONS.isEmpty()) target.sendTitle(legacy(title), legacy(subtitle),
                timings.fadeInTicks(), timings.stayTicks(), timings.fadeOutTicks());
        else EXTENSIONS.get(0).title(target, Objects.requireNonNull(title, "title"), Objects.requireNonNull(subtitle, "subtitle"), timings);
    }
    public void title(Player player, TextRef title, TextRef subtitle, Map<String, ?> placeholders, FeedbackTimings timings) {
        title(player, resolve(player, title, placeholders), resolve(player, subtitle, placeholders), timings);
    }

    public void clearTitle(Player player) {
        ensureOpen(); Player target = Objects.requireNonNull(player, "player");
        if (EXTENSIONS.isEmpty()) target.resetTitle(); else EXTENSIONS.get(0).clearTitle(target);
    }

    public void sound(Player player, Sound sound, float volume, float pitch) {
        validateSound(volume, pitch);
        Player target = Objects.requireNonNull(player, "player");
        target.playSound(target.getLocation(), Objects.requireNonNull(sound, "sound"), volume, pitch);
    }

    /** Plays a namespaced resource-pack sound only to this player. */
    public void sound(Player player, String soundKey, float volume, float pitch) {
        validateSound(volume, pitch);
        if (soundKey == null || !soundKey.matches("[a-z0-9._-]+:[a-z0-9._/-]+"))
            throw new IllegalArgumentException("Sound key must be namespaced");
        Player target = Objects.requireNonNull(player, "player");
        target.playSound(target.getLocation(), soundKey, volume, pitch);
    }

    public ManagedBossBar bossBar(Component title, BarColor color, BarStyle style) {
        ensureOpen();
        BossBar bar = Bukkit.createBossBar(legacy(title), Objects.requireNonNull(color, "color"), Objects.requireNonNull(style, "style"));
        ManagedBossBar managed = new ManagedBossBar(bar);
        bossBars.add(managed);
        return managed;
    }

    private Component resolve(Player player, TextRef reference, Map<String, ?> placeholders) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(placeholders, "placeholders");
        if (textProvider == null) throw new IllegalStateException("No TextProvider was configured for localized feedback");
        Locale locale = Locale.forLanguageTag(Objects.requireNonNull(player, "player").getLocale().replace('_', '-'));
        if (locale.getLanguage().isEmpty()) locale = Locale.ENGLISH;
        return textProvider.resolve(reference, locale, placeholders);
    }

    private static String legacy(Component component) { return LEGACY.serialize(Objects.requireNonNull(component, "component")); }
    private static void validateSound(float volume, float pitch) {
        if (!Float.isFinite(volume) || volume < 0) throw new IllegalArgumentException("Volume must be finite and non-negative");
        if (!Float.isFinite(pitch) || pitch < 0 || pitch > 2) throw new IllegalArgumentException("Pitch must be between 0 and 2");
    }
    private void ensureOpen() { if (closed) throw new IllegalStateException("BukkitFeedback is closed"); }
    public boolean isClosed() { return closed; }
    public int ownedBossBarCount() { return bossBars.size(); }

    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) {
        for (ManagedBossBar bar : new ArrayList<>(bossBars)) bar.removeViewer(event.getPlayer());
    }

    @EventHandler public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin)) close();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        for (ManagedBossBar bar : new ArrayList<>(bossBars)) bar.close();
        HandlerList.unregisterAll(this);
    }

    /** Owned Bukkit boss bar. Close it to remove all viewers and release it from this feedback service. */
    public final class ManagedBossBar implements AutoCloseable {
        private final BossBar bar;
        private boolean disposed;
        private ManagedBossBar(BossBar bar) { this.bar = bar; }
        public void addViewer(Player player) { check(); bar.addPlayer(Objects.requireNonNull(player, "player")); }
        public void removeViewer(Player player) { if (!disposed) bar.removePlayer(Objects.requireNonNull(player, "player")); }
        public void title(Component title) { check(); bar.setTitle(legacy(title)); }
        public void progress(double progress) {
            check();
            if (!Double.isFinite(progress) || progress < 0 || progress > 1) throw new IllegalArgumentException("Boss bar progress must be between 0 and 1");
            bar.setProgress(progress);
        }
        public void color(BarColor color) { check(); bar.setColor(Objects.requireNonNull(color, "color")); }
        public void style(BarStyle style) { check(); bar.setStyle(Objects.requireNonNull(style, "style")); }
        public void show() { check(); bar.setVisible(true); }
        public void hide() { check(); bar.setVisible(false); }
        public int viewerCount() { return disposed ? 0 : bar.getPlayers().size(); }
        public boolean isClosed() { return disposed; }
        private void check() { ensureOpen(); if (disposed) throw new IllegalStateException("Boss bar is closed"); }
        @Override public void close() {
            if (disposed) return;
            disposed = true;
            bar.removeAll();
            bar.setVisible(false);
            bossBars.remove(this);
        }
    }
}
