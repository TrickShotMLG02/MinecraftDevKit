package com.trickshotmlg.devkit.scheduler;

import java.util.concurrent.Callable;

/** Tick-based tasks dispatched asynchronously. Callbacks must obey the platform's thread-safety rules. */
public interface AsyncTaskRunner extends TaskRunner {
    /** Runs blocking or CPU work off the adapter's synchronous context and exposes its result. */
    <T> AsyncTaskResult<T> submit(Callable<T> action);
}
