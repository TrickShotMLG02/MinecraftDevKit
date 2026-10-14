package com.trickshotmlg.devkit.permissions;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * An immutable declaration of one namespaced permission node.
 *
 * <p>A permission describes an identifier and metadata only. Its dotted name and any child names
 * do not imply authorization inheritance.</p>
 */
public final class Permission implements PermissionMember {
    private final PermissionNamespace owner;
    private final String namespace;
    private final String name;
    private final String node;
    private final String description;
    private final DefaultAccess defaultAccess;

    Permission(PermissionNamespace owner, String namespace, String name,
               String description, DefaultAccess defaultAccess) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.name = Objects.requireNonNull(name, "name");
        this.node = namespace + "." + name;
        this.description = description;
        this.defaultAccess = Objects.requireNonNull(defaultAccess, "defaultAccess");
    }

    /**
     * Returns the fully qualified permission node, such as {@code friendnet.friends.add}.
     * @return fully qualified node
     */
    public String node() {
        return node;
    }

    /**
     * Returns the permission path relative to its namespace, such as {@code friends.add}.
     * @return relative permission path
     */
    public String name() {
        return name;
    }

    /**
     * Returns the namespace prefix, such as {@code friendnet}.
     * @return namespace prefix
     */
    public String namespace() {
        return namespace;
    }

    /**
     * Returns optional documentation, or {@code null} when none was declared.
     * @return permission description, or {@code null}
     */
    public String description() {
        return description;
    }

    /**
     * Returns the default access metadata for platform adapters.
     * @return default access metadata
     */
    public DefaultAccess defaultAccess() {
        return defaultAccess;
    }

    /**
     * Declares a child name under this permission. This is a naming convenience only; it does not
     * grant the child when this permission is granted.
     *
     * @param childPath one or more dot-separated path segments relative to this permission
     * @return the newly registered immutable permission
     * @throws IllegalArgumentException if this permission is not registered or the child path is invalid
     * @throws IllegalStateException if its namespace has already been built
     */
    public Permission child(String childPath) {
        return owner.child(this, childPath, null);
    }

    /**
     * Declares a child permission with optional metadata.
     * @param childPath one or more dot-separated path segments relative to this permission
     * @param options callback that configures immutable metadata
     * @return the newly registered immutable permission
     */
    public Permission child(String childPath, Consumer<PermissionOptions> options) {
        return owner.child(this, childPath, Objects.requireNonNull(options, "options"));
    }

    boolean belongsTo(PermissionNamespace namespaceOwner) {
        return owner == namespaceOwner;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Permission permission
                && owner == permission.owner && name.equals(permission.name);
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(owner) + name.hashCode();
    }

    @Override
    public String toString() {
        return node;
    }
}
