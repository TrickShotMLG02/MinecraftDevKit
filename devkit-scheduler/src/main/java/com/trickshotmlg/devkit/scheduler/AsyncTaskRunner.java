package com.trickshotmlg.devkit.scheduler;

import java.util.concurrent.Callable;

/** Tick-based tasks dispatched asynchronously. Callbacks must obey the platform's thread-safety rules. */
public interface AsyncTaskRunner extends TaskRunner {
    /**
     * Runs blocking or CPU work off the adapter's synchronous context and exposes its result.
     * Cancellation prevents queued continuations, but may not interrupt user code already running.
     * @param action computation to run asynchronously
     * @param <T> computation result type
     * @return an owned result pipeline
     */
    <T> AsyncTaskResult<T> submit(Callable<T> action);
}
