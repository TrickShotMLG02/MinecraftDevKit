package com.trickshotmlg.devkit.scheduler;

/** Root task owner exposing synchronous and asynchronous scheduling contexts. */
public interface TaskScheduler extends AutoCloseable {
    SyncTaskRunner sync();
    AsyncTaskRunner async();
    TaskScope scope();
    boolean isClosed();
    @Override void close();
}
