package com.trickshotmlg.devkit.lifecycle;

/** Synchronously creates a service after its declared dependencies have started. */
@FunctionalInterface
public interface ServiceFactory<T> {
    /** Creates the service value; returning {@code null} is a startup failure. */
    T create(LifecycleContext context) throws Exception;
}
