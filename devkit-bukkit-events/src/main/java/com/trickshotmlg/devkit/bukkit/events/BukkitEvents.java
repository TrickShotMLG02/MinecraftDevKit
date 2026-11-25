package com.trickshotmlg.devkit.bukkit.events;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Lightweight typed subscriptions backed directly by Bukkit's event manager. */
public final class BukkitEvents implements AutoCloseable {
    private final Object lock = new Object();
    private final Set<OwnedSubscription<?>> subscriptions = ConcurrentHashMap.newKeySet();
    private final Listener shutdownListener = new Listener() { };
    private Plugin plugin;
    private final PluginManager pluginManager;
    private boolean closed;

    private BukkitEvents(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.pluginManager = plugin.getServer().getPluginManager();
        try {
            pluginManager.registerEvent(PluginDisableEvent.class, shutdownListener, EventPriority.MONITOR,
                    (listener, event) -> {
                        if (((PluginDisableEvent) event).getPlugin() == this.plugin) close();
                    }, plugin, true);
        } catch (RuntimeException | Error failure) {
            HandlerList.unregisterAll(shutdownListener);
            this.plugin = null;
            throw failure;
        }
    }

    /**
     * Creates a registrar that also closes its subscriptions when the owning plugin disables.
     * @param plugin owner of these listener registrations
     * @return the event registrar
     */
    public static BukkitEvents create(Plugin plugin) { return new BukkitEvents(plugin); }

    /**
     * Subscribes at NORMAL priority and receives both cancelled and uncancelled events.
     * @param eventType event class
     * @param callback typed callback
     * @param <E> event type
     * @return an individually closeable subscription
     */
    public <E extends Event> EventSubscription subscribe(Class<E> eventType, Consumer<? super E> callback) {
        return subscribe(eventType, EventPriority.NORMAL, false, ignored -> true, callback);
    }

    /**
     * Subscribes with Bukkit's event priority and cancellation policy. MONITOR is intended for
     * observation; Bukkit plugins should not modify event outcomes at that priority.
     * @param eventType event class
     * @param priority Bukkit event priority
     * @param ignoreCancelled whether Bukkit should skip events already cancelled
     * @param callback typed callback
     * @param <E> event type
     * @return an individually closeable subscription
     */
    public <E extends Event> EventSubscription subscribe(Class<E> eventType, EventPriority priority,
                                                           boolean ignoreCancelled,
                                                           Consumer<? super E> callback) {
        return subscribe(eventType, priority, ignoreCancelled, ignored -> true, callback);
    }

    /**
     * Registers a typed callback. A false filter result skips only this callback. The callback runs
     * on Bukkit's event-dispatch thread; asynchronous events remain asynchronous. Closing a handle
     * prevents later dispatches from invoking the callback, but cannot stop a callback already running.
     * @param eventType event class
     * @param priority Bukkit event priority
     * @param ignoreCancelled whether Bukkit should skip events already cancelled
     * @param filter predicate evaluated on the dispatch thread
     * @param callback typed callback
     * @param <E> event type
     * @return an individually closeable subscription
     */
    public <E extends Event> EventSubscription subscribe(Class<E> eventType, EventPriority priority,
                                                           boolean ignoreCancelled,
                                                           Predicate<? super E> filter,
                                                           Consumer<? super E> callback) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(callback, "callback");
        synchronized (lock) {
            ensureOpen();
            OwnedSubscription<E> owned = new OwnedSubscription<>(eventType, filter, callback);
            try {
                plugin.getServer().getPluginManager().registerEvent(eventType, owned.listener, priority,
                        owned.executor, plugin, ignoreCancelled);
                subscriptions.add(owned);
                return owned;
            } catch (RuntimeException | Error failure) {
                owned.close();
                throw failure;
            }
        }
    }

    /** Current number of open subscriptions owned by this registrar.
     * @return open subscription count
     */
    public int subscriptionCount() { return subscriptions.size(); }

    @Override public void close() {
        ArrayList<OwnedSubscription<?>> owned;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            owned = new ArrayList<>(subscriptions);
            subscriptions.clear();
            plugin = null;
        }
        owned.forEach(OwnedSubscription::close);
        HandlerList.unregisterAll(shutdownListener);
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("BukkitEvents is closed");
    }

    private final class OwnedSubscription<E extends Event> implements EventSubscription {
        private final Class<E> eventType;
        private final Listener listener = new Listener() { };
        private final Predicate<? super E> filter;
        private final Consumer<? super E> callback;
        private volatile boolean subscriptionClosed;
        private final EventExecutor executor;

        private OwnedSubscription(Class<E> eventType, Predicate<? super E> filter, Consumer<? super E> callback) {
            this.eventType = eventType;
            this.filter = filter;
            this.callback = callback;
            this.executor = (ignored, event) -> {
                if (subscriptionClosed || !eventType.isInstance(event)) return;
                E typedEvent = eventType.cast(event);
                try {
                    if (!filter.test(typedEvent) || subscriptionClosed) return;
                    callback.accept(typedEvent);
                } catch (Throwable failure) {
                    if (failure instanceof Error error) throw error;
                    if (failure instanceof EventException eventException) throw eventException;
                    throw new EventException(failure);
                }
            };
        }

        @Override public boolean isClosed() { return subscriptionClosed; }

        @Override public void close() {
            if (subscriptionClosed) return;
            subscriptionClosed = true;
            HandlerList.unregisterAll(listener);
            subscriptions.remove(this);
        }
    }
}
    /**
     * Subscribes with Bukkit's event priority and cancellation policy. MONITOR is intended for
     * observation; Bukkit plugins should not modify event outcomes at that priority.
     */
