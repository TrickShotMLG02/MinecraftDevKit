package com.trickshotmlg.devkit.permissions;

import java.util.List;
import java.util.Objects;

/**
 * An immutable, named organizational selection of permissions and nested sections.
 *
 * <p>A section is not a permission node. Its name is metadata and has no authorization behavior.</p>
 */
public final class PermissionSection implements PermissionMember {
    private final PermissionNamespace owner;
    private final String namespace;
    private final String name;
    private final List<PermissionMember> members;

    PermissionSection(PermissionNamespace owner, String namespace, String name,
                      List<PermissionMember> members) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.name = Objects.requireNonNull(name, "name");
        this.members = List.copyOf(members);
    }

    /**
     * Returns the organizational name relative to the namespace.
     * @return section name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the namespace prefix.
     * @return namespace prefix
     */
    public String namespace() {
        return namespace;
    }

    /**
     * Returns the immutable, directly declared members in declaration order.
     * @return direct members
     */
    public List<PermissionMember> members() {
        return members;
    }

    boolean belongsTo(PermissionNamespace namespaceOwner) {
        return owner == namespaceOwner;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PermissionSection section
                && owner == section.owner && name.equals(section.name);
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(owner) + name.hashCode();
    }

    @Override
    public String toString() {
        return namespace + ":section:" + name;
    }
}
