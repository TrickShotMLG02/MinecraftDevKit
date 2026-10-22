package com.trickshotmlg.devkit.bukkit.gui;

import com.trickshotmlg.devkit.text.TextProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/** Owns managed inventories and centrally protects and dispatches their interactions. */
public final class GuiManager implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final TextProvider textProvider;
    private final Map<UUID, GuiSession> sessions = new HashMap<>();
    private boolean closed;

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
        render(session);
        player.openInventory(session.inventory());
        return session;
    }

    /** Refreshes all currently open instances of this immutable definition. */
    public void refreshOpen(GuiMenu menu) {
        requireMainThread();
        for (GuiSession session : sessions.values().toArray(GuiSession[]::new)) if (session.menu() == menu) render(session);
    }
    public void refresh(GuiSession session) {
        requireMainThread();
        if (isCurrent(session)) render(session);
    }
    public void refreshSlot(GuiSession session, int slot) {
        requireMainThread();
        if (!isCurrent(session)) return;
        if (slot < 0 || slot >= session.menu().size()) throw new IllegalArgumentException("Slot outside current menu: " + slot);
        GuiComponent component = session.menu().components().get(slot);
        if (component == null) { session.inventory().clear(slot); return; }
        refreshSlotIfCurrent(session, slot, component);
    }
    public void navigate(GuiSession session, GuiMenu child) {
        scheduleNavigation(session, () -> {
            session.history().push(session.menu());
            switchMenu(session, child);
        });
    }
    public void replace(GuiSession session, GuiMenu menu) {
        scheduleNavigation(session, () -> switchMenu(session, menu));
    }
    public void back(GuiSession session) {
        scheduleNavigation(session, () -> {
            if (!session.history().isEmpty()) switchMenu(session, session.history().pop());
        });
    }
    public void close(GuiSession session) {
        scheduleNavigation(session, () -> {
            sessions.remove(session.viewerId(), session);
            session.close();
            if (session.viewer().isOnline()) session.viewer().closeInventory();
        });
    }
    private void switchMenu(GuiSession session, GuiMenu menu) {
        session.transitioning(true);
        try {
            session.menu(menu);
            session.inventory(null);
            render(session);
            session.viewer().openInventory(session.inventory());
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
    void requireSessionThread() { requireMainThread(); }
    /** Refreshes a slot only while the session still displays the same component instance. */
    public void refreshSlotIfCurrent(GuiSession session, int slot, GuiComponent expected) {
        requireMainThread();
        Objects.requireNonNull(expected, "expected");
        if (!isCurrent(session) || session.menu().components().get(slot) != expected) return;
        GuiRenderContext context = new GuiRenderContext(session.viewer(), session.menu(), session, slot, expected);
        if (!expected.visible(context)) { session.inventory().clear(slot); return; }
        org.bukkit.inventory.ItemStack desired = expected.render(context);
        org.bukkit.inventory.ItemStack current = session.inventory().getItem(slot);
        if (!Objects.equals(current, desired)) session.inventory().setItem(slot, desired == null ? null : desired.clone());
    }
    public int openSessionCount() { return sessions.size(); }

    /** Schedules a platform mutation on the server thread and ignores it after manager shutdown. */
    public void executeOnMainThread(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (closed) return;
        if (Bukkit.isPrimaryThread()) action.run();
        else Bukkit.getScheduler().runTask(plugin, () -> { if (!closed) action.run(); });
    }
    void reportAsyncFailure(String message, Throwable failure) {
        plugin.getLogger().log(Level.WARNING, message + ": " + failure.getMessage());
    }

    private void render(GuiSession session) {
        GuiMenu menu = session.menu();
        Inventory inventory = session.inventory();
        if (inventory == null) {
            ManagedHolder holder = new ManagedHolder(session);
            inventory = Bukkit.createInventory(holder, menu.size(), menu.title(new GuiRenderContext(session.viewer(), menu, session)));
            holder.inventory = inventory;
            session.inventory(inventory);
        }
        inventory.clear();
        for (Map.Entry<Integer, GuiComponent> entry : menu.components().entrySet()) {
            GuiComponent component = entry.getValue();
            GuiRenderContext context = new GuiRenderContext(session.viewer(), menu, session, entry.getKey(), component);
            if (!component.visible(context)) continue;
            org.bukkit.inventory.ItemStack item = component.render(context);
            if (item != null) inventory.setItem(entry.getKey(), item.clone());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof ManagedHolder holder)) return;
        GuiSession session = holder.session;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !isCurrent(session)
                || !session.viewerId().equals(player.getUniqueId()) || session.inventory() != holder.inventory) return;
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= session.menu().size()) {
            if (affectsTopInventory(event.getAction(), event.getClick())) event.setCancelled(true);
            return;
        }
        GuiComponent component = session.menu().components().get(rawSlot);
        if (component == null || !component.interactive()) return;
        GuiRenderContext renderContext = new GuiRenderContext(player, session.menu(), session);
        if (!component.visible(renderContext)) return;
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
                && holder.session.inventory() == holder.inventory
                && event.getRawSlots().stream().anyMatch(slot -> slot < holder.session.menu().size())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof ManagedHolder holder)) return;
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

    private static boolean affectsTopInventory(InventoryAction action, org.bukkit.event.inventory.ClickType click) {
        return switch (action) {
            case MOVE_TO_OTHER_INVENTORY, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD, COLLECT_TO_CURSOR,
                    SWAP_WITH_CURSOR, PLACE_ALL, PLACE_ONE, PLACE_SOME, DROP_ALL_SLOT, DROP_ONE_SLOT -> true;
            default -> click == org.bukkit.event.inventory.ClickType.NUMBER_KEY
                    || click == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                    || click == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK;
        };
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
        for (GuiSession session : sessions.values().toArray(GuiSession[]::new)) {
            Player player = session.viewer();
            session.close();
            if (player.isOnline()) player.closeInventory();
        }
        sessions.clear();
    }

    private static final class ManagedHolder implements InventoryHolder {
        private final GuiSession session;
        private Inventory inventory;
        private ManagedHolder(GuiSession session) { this.session = session; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
