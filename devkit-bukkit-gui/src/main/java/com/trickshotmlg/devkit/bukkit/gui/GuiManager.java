package com.trickshotmlg.devkit.bukkit.gui;

import com.trickshotmlg.devkit.text.TextProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Owns managed inventories and centrally protects and dispatches their interactions. */
public final class GuiManager implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final TextProvider textProvider;
    private final Map<UUID, GuiSession> sessions = new HashMap<>();
    private final Map<GuiMenu, List<Listener>> refreshListeners = new IdentityHashMap<>();
    private final Map<GuiSession, RefreshScope> pendingRefresh = new IdentityHashMap<>();
    private BukkitTask refreshTask;
    private volatile boolean closed;

    private GuiManager(Plugin plugin, TextProvider textProvider) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.textProvider = textProvider;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    public static GuiManager create(Plugin plugin) { return new GuiManager(plugin, null); }
    public static GuiManager create(Plugin plugin, TextProvider textProvider) { return new GuiManager(plugin, Objects.requireNonNull(textProvider, "textProvider")); }

    /** Opens the menu as a new root session and returns the viewer's isolated state. */
    public GuiSession open(Player player, GuiMenu menu) {
        requireMainThread();
        ensureOpen();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(menu, "menu");
        GuiSession old = sessions.remove(player.getUniqueId());
        if (old != null) old.close();
        GuiSession session = new GuiSession(player, menu, textProvider, this);
        sessions.put(player.getUniqueId(), session);
        registerRefreshBindings(menu);
        try {
            render(session);
            player.openInventory(session.inventory());
        } catch (RuntimeException failure) {
            sessions.remove(player.getUniqueId(), session);
            session.close();
            throw failure;
        }
        return session;
    }

    /** Refreshes all currently open instances of this immutable definition. */
    public void refreshOpen(GuiMenu menu) {
        requireMainThread();
        for (GuiSession session : sessions.values().toArray(GuiSession[]::new)) if (session.menu() == menu) queueRefresh(session, RefreshScope.FULL);
    }
    public void refresh(GuiSession session) {
        requireMainThread();
        if (isCurrent(session)) render(session);
    }
    public void refreshSlot(GuiSession session, int slot) {
        requireMainThread();
        if (!isCurrent(session)) return;
        if (slot < 0 || slot >= session.menu().size()) throw new IllegalArgumentException("Slot outside current menu: " + slot);
        if (session.menu().refreshesContentAt(slot)) {
            renderContent(session);
            return;
        }
        GuiComponent component = session.menu().components().get(slot);
        if (component == null) {
            org.bukkit.inventory.ItemStack background = backgroundItem(session, slot);
            if (!Objects.equals(session.inventory().getItem(slot), background)) session.inventory().setItem(slot, background);
            return;
        }
        refreshSlotIfCurrent(session, slot, component);
    }
    public void navigate(GuiSession session, GuiMenu child) {
        scheduleNavigation(session, () -> {
            GuiMenu previous = session.menu();
            switchMenu(session, child);
            session.history().push(previous);
        });
    }
    public void replace(GuiSession session, GuiMenu menu) {
        scheduleNavigation(session, () -> switchMenu(session, menu));
    }
    public void back(GuiSession session) {
        scheduleNavigation(session, () -> {
            if (!session.history().isEmpty()) {
                GuiMenu previous = session.history().peek();
                switchMenu(session, previous);
                session.history().pop();
            }
        });
    }
    public void close(GuiSession session) {
        scheduleNavigation(session, () -> {
            sessions.remove(session.viewerId(), session);
            Player player = session.viewer();
            session.close();
            if (player.isOnline()) player.closeInventory();
        });
    }
    private void switchMenu(GuiSession session, GuiMenu menu) {
        GuiMenu previousMenu = session.menu();
        Inventory previousInventory = session.inventory();
        session.transitioning(true);
        try {
            session.menu(menu);
            session.inventory(null);
            render(session);
            registerRefreshBindings(menu);
            session.viewer().openInventory(session.inventory());
        } catch (RuntimeException failure) {
            session.menu(previousMenu);
            session.inventory(previousInventory);
            if (session.viewer().isOnline() && previousInventory != null) session.viewer().openInventory(previousInventory);
            throw failure;
        } finally {
            session.transitioning(false);
        }
    }
    private void scheduleNavigation(GuiSession session, Runnable operation) {
        requireMainThread();
        if (!isCurrent(session)) return;
        long version = session.nextNavigationVersion();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!isCurrent(session) || session.navigationVersion() != version
                    || !session.viewer().isOnline()) return;
            try { operation.run(); }
            catch (Throwable failure) { plugin.getLogger().log(Level.SEVERE, "GUI navigation failed", failure); }
        });
    }
    private void registerRefreshBindings(GuiMenu menu) {
        if (refreshListeners.containsKey(menu) || menu.refreshBindings().isEmpty()) return;
        List<Listener> listeners = new ArrayList<>();
        for (GuiRefreshBinding<?> binding : menu.refreshBindings()) {
            Listener listener = new Listener() { };
            EventExecutor executor = (ignored, event) -> {
                Runnable refresh = () -> {
                    try {
                        if (binding.matches(event)) for (GuiSession session : sessions.values().toArray(GuiSession[]::new))
                            if (session.menu() == menu) queueRefresh(session, binding.scope());
                    } catch (Throwable failure) {
                        plugin.getLogger().log(Level.WARNING, "GUI refresh event filter failed", failure);
                    }
                };
                if (event.isAsynchronous()) executeOnMainThread(refresh);
                else refresh.run();
            };
            Bukkit.getPluginManager().registerEvent(binding.eventType(), listener, EventPriority.MONITOR, executor, plugin, true);
            listeners.add(listener);
        }
        refreshListeners.put(menu, List.copyOf(listeners));
    }
    private void queueRefresh(GuiSession session, RefreshScope scope) {
        if (!isCurrent(session)) return;
        pendingRefresh.merge(session, Objects.requireNonNull(scope, "scope"),
                (oldScope, newScope) -> oldScope == RefreshScope.FULL || newScope == RefreshScope.FULL
                        ? RefreshScope.FULL : RefreshScope.CONTENT);
        if (refreshTask != null) return;
        refreshTask = Bukkit.getScheduler().runTask(plugin, () -> {
            refreshTask = null;
            Map<GuiSession, RefreshScope> pending = new IdentityHashMap<>(pendingRefresh);
            pendingRefresh.clear();
            for (Map.Entry<GuiSession, RefreshScope> entry : pending.entrySet()) if (isCurrent(entry.getKey())) {
                try {
                    if (entry.getValue() == RefreshScope.CONTENT) renderContent(entry.getKey());
                    else render(entry.getKey());
                }
                catch (RuntimeException failure) { plugin.getLogger().log(Level.WARNING, "GUI refresh failed", failure); }
            }
        });
    }
    void requireSessionThread() { requireMainThread(); }
    /** Refreshes a slot only while the session still displays the same component instance. */
    public void refreshSlotIfCurrent(GuiSession session, int slot, GuiComponent expected) {
        requireMainThread();
        Objects.requireNonNull(expected, "expected");
        if (!isCurrent(session)) return;
        if (slot < 0 || slot >= session.menu().size()) throw new IllegalArgumentException("Slot outside current menu: " + slot);
        if (session.menu().components().get(slot) != expected) return;
        if (session.menu().refreshesContentAt(slot)) {
            renderContent(session);
            return;
        }
        GuiRenderContext context = new GuiRenderContext(session.viewer(), session.menu(), session, slot, expected);
        org.bukkit.inventory.ItemStack desired;
        try { desired = expected.visible(context) ? expected.render(context) : null; }
        catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "GUI component refresh failed at slot " + slot, failure);
            return;
        }
        if (desired == null) desired = backgroundItem(session, slot);
        org.bukkit.inventory.ItemStack current = session.inventory().getItem(slot);
        if (!Objects.equals(current, desired)) session.inventory().setItem(slot, desired == null ? null : desired.clone());
    }
    public int openSessionCount() { return sessions.size(); }

    /** Schedules a platform mutation on the server thread and ignores it after manager shutdown. */
    public void executeOnMainThread(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (closed) return;
        // Always defer, including when called on the main thread, so render callbacks cannot
        // re-enter the renderer and have their result overwritten by the in-progress pass.
        try {
            Bukkit.getScheduler().runTask(plugin, () -> { if (!closed) action.run(); });
        } catch (org.bukkit.plugin.IllegalPluginAccessException failure) {
            if (!closed) throw failure;
        }
    }
    void reportAsyncFailure(String message, Throwable failure) {
        plugin.getLogger().log(Level.WARNING, message + ": " + failure.getMessage());
    }

    private void render(GuiSession session) {
        render(session, null);
    }
    private void renderContent(GuiSession session) {
        if (session.menu().contentRefreshSlots().isEmpty()) render(session);
        else render(session, session.menu().contentRefreshSlots());
    }
    private void render(GuiSession session, Set<Integer> requestedSlots) {
        session.beginRender();
        GuiMenu menu = session.menu();
        Inventory inventory = session.inventory();
        Set<Integer> slots = requestedSlots;
        if (inventory == null) {
            ManagedHolder holder = new ManagedHolder(this, session, menu);
            inventory = Bukkit.createInventory(holder, menu.size(), menu.title(new GuiRenderContext(session.viewer(), menu, session)));
            holder.inventory = inventory;
            session.inventory(inventory);
            slots = null;
        }
        org.bukkit.inventory.ItemStack[] desired = new org.bukkit.inventory.ItemStack[menu.size()];
        Iterable<Integer> renderSlots = slots == null ? java.util.stream.IntStream.range(0, desired.length).boxed().toList() : slots;
        for (int slot : renderSlots) {
            try { desired[slot] = backgroundItem(session, slot); }
            catch (RuntimeException failure) { plugin.getLogger().log(Level.WARNING, "GUI background pattern failed at slot " + slot, failure); }
        }
        for (Map.Entry<Integer, GuiComponent> entry : menu.components().entrySet()) {
            if (slots != null && !slots.contains(entry.getKey())) continue;
            GuiComponent component = entry.getValue();
            GuiRenderContext context = new GuiRenderContext(session.viewer(), menu, session, entry.getKey(), component);
            try {
                if (!component.visible(context)) continue;
                org.bukkit.inventory.ItemStack item = component.render(context);
                if (item != null) desired[entry.getKey()] = item.clone();
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "GUI component render failed at slot " + entry.getKey(), failure);
            }
        }
        for (int slot : renderSlots) {
            org.bukkit.inventory.ItemStack current = inventory.getItem(slot);
            if (!Objects.equals(current, desired[slot])) inventory.setItem(slot, desired[slot] == null ? null : desired[slot].clone());
        }
    }
    private org.bukkit.inventory.ItemStack backgroundItem(GuiSession session, int slot) {
        GuiMenu menu = session.menu();
        if (menu.background() == null) return null;
        GuiRenderContext context = new GuiRenderContext(session.viewer(), menu, session, slot);
        org.bukkit.inventory.ItemStack item = menu.background().itemAt(
                new GuiPatternContext(slot, slot / 9, slot % 9, menu.rows(), context));
        return item == null ? null : item.clone();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof ManagedHolder holder)
                || !GuiInteractionRules.ownedBy(this, holder.manager)) return;
        GuiSession session = holder.session;
        int rawSlot = event.getRawSlot();
        boolean topSlot = rawSlot >= 0 && rawSlot < event.getView().getTopInventory().getSize();
        if (GuiInteractionRules.cancelClick(holder.menu.interactionPolicy(), rawSlot,
                event.getView().getTopInventory().getSize(), event.getAction())) event.setCancelled(true);
        if (!topSlot || !(event.getWhoClicked() instanceof Player player) || !isCurrent(session)
                || holder.menu != session.menu() || !session.viewerId().equals(player.getUniqueId())
                || session.inventory() != holder.inventory) return;
        GuiComponent component = session.menu().components().get(rawSlot);
        if (component == null || !component.interactive()) return;
        GuiRenderContext renderContext = new GuiRenderContext(player, session.menu(), session, rawSlot, component);
        try { if (!component.visible(renderContext)) return; }
        catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "GUI component visibility check failed at slot " + rawSlot, failure);
            return;
        }
        if (!isCurrent(session) || session.inventory() != holder.inventory) return;
        try {
            component.click(new GuiClickContext(player, session.menu(), session, rawSlot, event.getClick(), event, this));
        } catch (Throwable failure) {
            plugin.getLogger().log(Level.SEVERE, "GUI click handler failed for " + session.menu(), failure);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ManagedHolder holder
                && GuiInteractionRules.ownedBy(this, holder.manager)
                && GuiInteractionRules.cancelDrag(holder.menu.interactionPolicy(),
                event.getView().getTopInventory().getSize(), event.getRawSlots())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof ManagedHolder holder)
                || holder.manager != this || !event.getPlayer().getUniqueId().equals(holder.session.viewerId())
                || event.getInventory() != holder.inventory) return;
        GuiSession session = holder.session;
        if (session.transitioning()) return;
        if (isCurrent(session) && session.inventory() == holder.inventory) {
            sessions.remove(session.viewerId());
            session.close();
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        GuiSession session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) session.close();
    }

    private boolean isCurrent(GuiSession session) { return !closed && session != null && !session.closed() && sessions.get(session.viewerId()) == session; }
    private void ensureOpen() { if (closed) throw new IllegalStateException("GuiManager is closed"); }
    private static void requireMainThread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("GUI operations must run on the server thread"); }

    /** Unregisters listeners and closes every managed session. Must be called on the server thread. */
    @Override public void close() {
        if (closed) return;
        requireMainThread();
        closed = true;
        org.bukkit.event.HandlerList.unregisterAll(this);
        for (List<Listener> listeners : refreshListeners.values()) for (Listener listener : listeners)
            org.bukkit.event.HandlerList.unregisterAll(listener);
        refreshListeners.clear();
        pendingRefresh.clear();
        if (refreshTask != null) { refreshTask.cancel(); refreshTask = null; }
        for (GuiSession session : sessions.values().toArray(GuiSession[]::new)) {
            Player player = session.viewer();
            session.close();
            if (player.isOnline()) player.closeInventory();
        }
        sessions.clear();
    }

    private static final class ManagedHolder implements InventoryHolder {
        private final GuiManager manager;
        private final GuiSession session;
        private final GuiMenu menu;
        private Inventory inventory;
        private ManagedHolder(GuiManager manager, GuiSession session, GuiMenu menu) {
            this.manager = manager;
            this.session = session;
            this.menu = menu;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
