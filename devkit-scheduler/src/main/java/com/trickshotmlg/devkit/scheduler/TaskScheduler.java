package com.trickshotmlg.devkit.scheduler;

/** Root task owner exposing synchronous and asynchronous scheduling contexts. */
public interface TaskScheduler extends AutoCloseable {
    /**
     * Root synchronous runner; its tasks are cancelled when this scheduler closes.
     * @return root runner
     */
    SyncTaskRunner sync();
    /**
     * Root asynchronous runner; its tasks are cancelled when this scheduler closes.
     * @return root runner
     */
    AsyncTaskRunner async();
    /**
     * Creates a child owner for a related group of work.
     * @return newly opened child scope
     */
    TaskScope scope();
    /**
     * Whether this scheduler has been closed.
     * @return true if closed
     */
    boolean isClosed();
    /** Cancels all tasks and scopes owned by this scheduler. Safe to call more than once. */
    @Override void close();
}
