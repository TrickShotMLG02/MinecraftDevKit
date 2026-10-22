package com.trickshotmlg.devkit.scheduler;

/** Owns related tasks and child scopes and cancels them together when closed. */
public interface TaskScope extends AutoCloseable {
    /**
     * Synchronous runner whose tasks belong to this scope.
     * @return scope-owned runner
     */
    SyncTaskRunner sync();
    /**
     * Asynchronous runner whose tasks and result continuations belong to this scope.
     * @return scope-owned runner
     */
    AsyncTaskRunner async();
    /**
     * Creates a child scope that is closed when this scope closes.
     * @return new child scope
     */
    TaskScope childScope();
    /**
     * Whether this scope has rejected new work and begun closing.
     * @return true if closed
     */
    boolean isClosed();
    /**
     * Number of non-terminal tasks directly owned by this scope, excluding child scopes.
     * @return directly owned non-terminal task count
     */
    int activeTaskCount();
    /** Cancels currently owned tasks and child scopes. Safe to call more than once. */
    @Override void close();
}
