package com.trickshotmlg.devkit.scheduler;

/** Owns related tasks and cancels them together when closed. */
public interface TaskScope extends AutoCloseable {
    SyncTaskRunner sync();
    AsyncTaskRunner async();
    TaskScope childScope();
    boolean isClosed();
    /** Number of currently owned non-terminal tasks. */
    int activeTaskCount();
    @Override void close();
}
