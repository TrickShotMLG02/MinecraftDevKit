package com.trickshotmlg.devkit.bukkit.events;

/** A handle for one narrowly-owned Bukkit event registration. */
public interface EventSubscription extends AutoCloseable {
    /**
     * Whether this registration has already been closed.
     * @return true after the owned Bukkit listener has been unregistered
     */
    boolean isClosed();

    /** Unregisters only the listener owned by this handle; safe to call more than once. */
    @Override
    void close();
}
