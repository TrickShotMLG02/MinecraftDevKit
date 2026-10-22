package com.trickshotmlg.devkit.lifecycle;

/** Explicit shutdown callback for services that do not implement {@link AutoCloseable}. */
@FunctionalInterface
public interface ServiceCleanup<T> {
    void close(T service, LifecycleContext context) throws Exception;
}
