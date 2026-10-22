package com.trickshotmlg.devkit.lifecycle;

import java.util.Objects;

/** Strongly typed identity for one lifecycle-managed service. */
public final class ServiceKey<T> implements LifecycleDependency {
    private final String name;
    private final Class<T> serviceType;

    private ServiceKey(String name, Class<T> serviceType) {
        this.name = requireName(name);
        this.serviceType = Objects.requireNonNull(serviceType, "serviceType");
    }

    /**
     * Creates a service key with a stable diagnostic name and runtime type.
     * @param name stable logical service name unique within a lifecycle definition
     * @param serviceType runtime service class
     * @param <T> service type
     * @return immutable typed service identity
     */
    public static <T> ServiceKey<T> of(String name, Class<T> serviceType) {
        return new ServiceKey<>(name, serviceType);
    }

    @Override public String name() { return name; }
    /** Runtime class used to validate service factory results. */
    public Class<T> serviceType() { return serviceType; }

    static String requireName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || !name.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")) {
            throw new IllegalArgumentException("Lifecycle names must be non-empty and contain only letters, digits, '.', '_' or '-': " + name);
        }
        return name;
    }

    @Override public boolean equals(Object other) {
        return other instanceof ServiceKey<?> key && name.equals(key.name) && serviceType.equals(key.serviceType);
    }
    @Override public int hashCode() { return Objects.hash(name, serviceType); }
    @Override public String toString() { return "ServiceKey[" + name + ":" + serviceType.getTypeName() + "]"; }
}
