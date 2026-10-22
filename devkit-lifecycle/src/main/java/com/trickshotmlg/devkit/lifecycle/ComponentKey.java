package com.trickshotmlg.devkit.lifecycle;

import java.util.Objects;

/** Named identity for a startup/shutdown component that does not publish a service value. */
public final class ComponentKey implements LifecycleDependency {
    private final String name;
    private ComponentKey(String name) { this.name = ServiceKey.requireName(name); }
    /** Creates a named identity for a non-service lifecycle component. */
    public static ComponentKey of(String name) { return new ComponentKey(name); }
    @Override public String name() { return name; }
    @Override public boolean equals(Object other) { return other instanceof ComponentKey key && name.equals(key.name); }
    @Override public int hashCode() { return Objects.hash(name); }
    @Override public String toString() { return "ComponentKey[" + name + "]"; }
}
