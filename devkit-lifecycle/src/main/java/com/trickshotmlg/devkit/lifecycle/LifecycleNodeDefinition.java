package com.trickshotmlg.devkit.lifecycle;

import java.util.List;

record LifecycleNodeDefinition(LifecycleDependency key, Class<?> serviceType,
                               ServiceFactory<?> factory, ServiceCleanup<Object> cleanup,
                               LifecycleAction start, LifecycleAction stop, LifecycleAction reload,
                               List<LifecycleDependency> dependencies, int registrationOrder) {
    String name() { return key.name(); }
    boolean service() { return serviceType != null; }
}
