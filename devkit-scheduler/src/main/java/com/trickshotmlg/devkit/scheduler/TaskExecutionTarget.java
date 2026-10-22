package com.trickshotmlg.devkit.scheduler;

/**
 * Execution contexts supported by the shared contract.
 *
 * <p>{@link #SYNC} means the adapter's documented synchronous context. In the Bukkit adapter
 * this is Bukkit's traditional global primary server thread. It does not imply that every
 * future platform has one globally valid thread for all world or entity operations.</p>
 */
public enum TaskExecutionTarget {
    SYNC,
    ASYNC
}
