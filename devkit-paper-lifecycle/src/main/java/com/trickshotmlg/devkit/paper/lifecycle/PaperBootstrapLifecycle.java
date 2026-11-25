package com.trickshotmlg.devkit.paper.lifecycle;

import io.papermc.paper.datapack.DatapackRegistrar;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.lifecycle.event.registrar.RegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEventType;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEvent;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Bootstrap-phase helper for Paper lifecycle registrations. It intentionally exposes only the
 * datapack-discovery registrar, not a JavaPlugin, Bukkit plugin manager, scheduler, or runtime
 * MinecraftDevKit service container.
 */
public final class PaperBootstrapLifecycle {
    private boolean datapackDiscoveryRegistered;

    /**
     * Registers one bootstrap-safe datapack discovery callback. Paper does not expose a supported
     * way to unregister accepted bootstrap lifecycle handlers; registration failures therefore
     * propagate and are not reported as rolled back.
     *
     * @param context current bootstrap context; it is not retained by this helper
     * @param callback operation receiving Paper's discovery registrar when the event fires
     * @return true when newly registered; false when already registered by this helper instance
     */
    public synchronized boolean registerDatapackDiscovery(
            BootstrapContext context, Consumer<? super DatapackRegistrar> callback) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(callback, "callback");
        return register(context, LifecycleEvents.DATAPACK_DISCOVERY,
                (RegistrarEvent<DatapackRegistrar> event) -> callback.accept(event.registrar()));
    }

    <E extends LifecycleEvent> boolean register(BootstrapContext context,
            LifecycleEventType<BootstrapContext, E, ?> eventType,
            Consumer<? super E> callback) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(callback, "callback");
        if (datapackDiscoveryRegistered) return false;

        context.getLifecycleManager().registerEventHandler(eventType, callback::accept);
        datapackDiscoveryRegistered = true;
        return true;
    }

    /** @return whether this helper has accepted its datapack discovery registration */
    public synchronized boolean isDatapackDiscoveryRegistered() { return datapackDiscoveryRegistered; }
}
