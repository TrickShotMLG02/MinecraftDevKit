package com.trickshotmlg.devkit.permissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An immutable group definition represented by its own permission node.
 *
 * <p>Inheritance selects a parent's effective ordinary permissions; it does not grant the
 * parent's group node. Exclusions remove permissions from this group's resolved grant set and do
 * not express a denial against grants assigned elsewhere.</p>
 */
public final class PermissionGroup {
    private final PermissionNamespace owner;
    private final String namespace;
    private final String name;
    private final String node;
    private final String description;
    private final DefaultAccess defaultAccess;
    private final List<PermissionGroup> inheritedGroups;
    private final List<PermissionMember> grants;
    private final List<PermissionMember> exclusions;
    private final boolean grantAllPermissions;

    PermissionGroup(PermissionNamespace owner, String namespace, String name, String description,
                    DefaultAccess defaultAccess, List<PermissionGroup> inheritedGroups,
                    List<PermissionMember> grants, List<PermissionMember> exclusions,
                    boolean grantAllPermissions) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.name = Objects.requireNonNull(name, "name");
        this.node = namespace + "." + name;
        this.description = description;
        this.defaultAccess = Objects.requireNonNull(defaultAccess, "defaultAccess");
        this.inheritedGroups = List.copyOf(inheritedGroups);
        this.grants = List.copyOf(grants);
        this.exclusions = List.copyOf(exclusions);
        this.grantAllPermissions = grantAllPermissions;
    }

    /**
     * Returns the fully qualified group permission node.
     * @return fully qualified node
     */
    public String node() {
        return node;
    }

    /**
     * Returns the group name relative to its namespace.
     * @return relative group name
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
     * Returns optional group documentation, or {@code null} when none was declared.
     * @return group description, or {@code null}
     */
    public String description() {
        return description;
    }

    /**
     * Returns the default access metadata for compatible platforms.
     * @return default access metadata
     */
    public DefaultAccess defaultAccess() {
        return defaultAccess;
    }

    /**
     * Returns the immutable groups whose effective grants this group inherits.
     * @return inherited group definitions
     */
    public List<PermissionGroup> inheritedGroups() {
        return inheritedGroups;
    }

    /**
     * Returns directly granted permissions and sections in declaration order.
     * @return direct grants
     */
    public List<PermissionMember> grants() {
        return grants;
    }

    /**
     * Returns directly excluded permissions and sections in declaration order.
     * @return direct exclusions
     */
    public List<PermissionMember> exclusions() {
        return exclusions;
    }

    /**
     * Returns whether all ordinary permissions in the completed catalog are selected.
     * @return {@code true} when all ordinary permissions are selected
     */
    public boolean grantsAllPermissions() {
        return grantAllPermissions;
    }

    boolean belongsTo(PermissionNamespace namespaceOwner) {
        return owner == namespaceOwner;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PermissionGroup group
                && owner == group.owner && name.equals(group.name);
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(owner) + name.hashCode();
    }

    @Override
    public String toString() {
        return node;
    }

    /** Short-lived mutable builder used only inside a namespace group declaration callback. */
    public static final class Builder {
        private final PermissionNamespace owner;
        private String description;
        private DefaultAccess defaultAccess = DefaultAccess.FALSE;
        private final List<PermissionGroup> inheritedGroups = new ArrayList<>();
        private final List<PermissionMember> grants = new ArrayList<>();
        private final List<PermissionMember> exclusions = new ArrayList<>();
        private boolean grantAllPermissions;
        private boolean frozen;

        Builder(PermissionNamespace owner) {
            this.owner = owner;
        }

        /**
         * Sets optional documentation for this group.
         * @param description non-blank description
         * @return this builder
         */
        public Builder description(String description) {
            ensureMutable();
            this.description = PermissionOptions.requireDescription(description);
            return this;
        }

        /**
         * Sets default access metadata. It does not affect permission resolution.
         * @param defaultAccess default access metadata
         * @return this builder
         */
        public Builder defaultAccess(DefaultAccess defaultAccess) {
            ensureMutable();
            this.defaultAccess = Objects.requireNonNull(defaultAccess, "defaultAccess");
            return this;
        }

        /**
         * Inherits the effective grants of one or more previously declared groups.
         * @param groups groups whose effective grants are inherited
         * @return this builder
         */
        public Builder inherits(PermissionGroup... groups) {
            ensureMutable();
            addNonNull(inheritedGroups, groups, "groups");
            return this;
        }

        /**
         * Grants one or more permissions or sections.
         * @param members permissions or sections to grant
         * @return this builder
         */
        public Builder grant(PermissionMember... members) {
            ensureMutable();
            addNonNull(grants, members, "members");
            return this;
        }

        /**
         * Excludes one or more permissions or sections from this group's effective grant set.
         * @param members permissions or sections to exclude
         * @return this builder
         */
        public Builder exclude(PermissionMember... members) {
            ensureMutable();
            addNonNull(exclusions, members, "members");
            return this;
        }

        /**
         * Selects every ordinary permission registered before the namespace is built.
         * @return this builder
         */
        public Builder grantAllPermissions() {
            ensureMutable();
            grantAllPermissions = true;
            return this;
        }

        PermissionGroup freeze(String namespace, String name) {
            ensureMutable();
            frozen = true;
            return new PermissionGroup(owner, namespace, name, description, defaultAccess,
                    inheritedGroups, grants, exclusions, grantAllPermissions);
        }

        void freezeAfterFailure() {
            frozen = true;
        }

        private static <T> void addNonNull(List<T> target, T[] values, String parameter) {
            Objects.requireNonNull(values, parameter);
            List<T> validated = new ArrayList<>(values.length);
            for (T value : values) {
                validated.add(Objects.requireNonNull(value, parameter + " contains null"));
            }
            target.addAll(validated);
        }

        private void ensureMutable() {
            if (frozen) {
                throw new IllegalStateException("Group builder is only valid inside its declaration callback");
            }
        }
    }
}
