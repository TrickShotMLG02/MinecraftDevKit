package com.trickshotmlg.devkit.lifecycle;

/** Creates a service during ordered lifecycle startup. */
@FunctionalInterface
public interface ServiceFactory<T> {
    T create(LifecycleContext context) throws Exception;
}
