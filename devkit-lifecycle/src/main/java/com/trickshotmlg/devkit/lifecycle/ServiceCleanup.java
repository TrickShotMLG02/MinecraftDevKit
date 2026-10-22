package com.trickshotmlg.devkit.lifecycle;

/** Explicit cleanup callback for a service that does not use automatic {@link AutoCloseable} ownership. */
@FunctionalInterface
public interface ServiceCleanup<T> {
    /** Closes or otherwise releases the service. */
    void close(T service, LifecycleContext context) throws Exception;
}
