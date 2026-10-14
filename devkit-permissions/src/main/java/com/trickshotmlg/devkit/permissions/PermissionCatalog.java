package com.trickshotmlg.devkit.permissions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, fully validated result of building a {@link PermissionNamespace}.
 *
 * <p>The catalog resolves reusable sections and group grant sets. It describes definitions; it
 * does not assign permissions to players, evaluate operators, or override a platform permission
 * manager.</p>
 */
public final class PermissionCatalog {
    private final PermissionNamespace owner;
    private final String namespace;
    private final Map<String, Permission> permissionsByName;
    private final Map<String, Permission> permissionsByNode;
    private final Map<String, PermissionSection> sectionsByName;
    private final Map<String, PermissionGroup> groupsByName;
    private final Map<String, PermissionGroup> groupsByNode;
    private final Set<Permission> permissions;
    private final Set<PermissionSection> sections;
    private final Set<PermissionGroup> groups;
    private final Map<PermissionSection, Set<Permission>> sectionPermissions;
    private final Map<PermissionGroup, Set<Permission>> groupPermissions;

    private PermissionCatalog(PermissionNamespace owner,
                              Map<String, Permission> permissions,
                              Map<String, PermissionSection> sections,
                              Map<String, PermissionGroup> groups) {
        this.owner = owner;
        this.namespace = owner.namespaceName();
        this.permissionsByName = immutableMap(permissions);
        this.sectionsByName = immutableMap(sections);
        this.groupsByName = immutableMap(groups);
        this.permissions = immutableSet(permissionsByName.values());
        this.sections = immutableSet(sectionsByName.values());
        this.groups = immutableSet(groupsByName.values());

        LinkedHashMap<String, Permission> permissionNodes = new LinkedHashMap<>();
        for (Permission permission : permissions.values()) {
            if (permissionNodes.putIfAbsent(permission.node(), permission) != null) {
                throw new IllegalArgumentException("Duplicate permission node '" + permission.node() + "'");
            }
        }
        LinkedHashMap<String, PermissionGroup> groupNodes = new LinkedHashMap<>();
        for (PermissionGroup group : groups.values()) {
            if (permissionNodes.containsKey(group.node())) {
                throw new IllegalArgumentException("Group node '" + group.node()
                        + "' collides with a permission node");
            }
            if (groupNodes.putIfAbsent(group.node(), group) != null) {
                throw new IllegalArgumentException("Duplicate group node '" + group.node() + "'");
            }
        }
        this.permissionsByNode = immutableMap(permissionNodes);
        this.groupsByNode = immutableMap(groupNodes);

        Resolver resolver = new Resolver(owner, permissions.values(), sections.values(), groups.values());
        this.sectionPermissions = resolver.resolveSections();
        this.groupPermissions = resolver.resolveGroups();
    }

    static PermissionCatalog create(PermissionNamespace owner,
                                    Map<String, Permission> permissions,
                                    Map<String, PermissionSection> sections,
                                    Map<String, PermissionGroup> groups) {
        return new PermissionCatalog(owner, permissions, sections, groups);
    }

    /**
     * Returns ordinary permission definitions in registration order. Group nodes are separate.
     * @return immutable ordered permission definitions
     */
    public Set<Permission> permissions() {
        return permissions;
    }

    /**
     * Returns section definitions in registration order.
     * @return immutable ordered sections
     */
    public Set<PermissionSection> sections() {
        return sections;
    }

    /**
     * Returns group definitions in registration order.
     * @return immutable ordered groups
     */
    public Set<PermissionGroup> groups() {
        return groups;
    }

    /**
     * Returns the deduplicated, immutable effective contents of a registered section.
     * @param section section to resolve
     * @return immutable ordered permissions in the section
     * @throws IllegalArgumentException if the section is not in this catalog
     */
    public Set<Permission> permissionsOf(PermissionSection section) {
        Objects.requireNonNull(section, "section");
        Set<Permission> result = sectionPermissions.get(section);
        if (result == null || !owner.containsSection(section)) {
            throw new IllegalArgumentException("Section is not part of namespace '" + namespace + "': " + section);
        }
        return result;
    }

    /**
     * Returns the immutable effective grants for a group, after transitive inheritance and that
     * group's exclusions. Exclusions are not negative assignments outside this group definition.
     * @param group group to resolve
     * @return immutable ordered effective permissions
     * @throws IllegalArgumentException if the group is not in this catalog
     */
    public Set<Permission> effectivePermissions(PermissionGroup group) {
        Objects.requireNonNull(group, "group");
        Set<Permission> result = groupPermissions.get(group);
        if (result == null || !owner.containsGroup(group)) {
            throw new IllegalArgumentException("Group is not part of namespace '" + namespace + "': " + group);
        }
        return result;
    }

    /**
     * Finds an ordinary permission by its fully qualified node.
     * @param node fully qualified permission node
     * @return the matching permission, if registered
     */
    public Optional<Permission> findPermission(String node) {
        return Optional.ofNullable(permissionsByNode.get(Objects.requireNonNull(node, "node")));
    }

    /**
     * Finds a group by its fully qualified group node.
     * @param node fully qualified group node
     * @return the matching group, if registered
     */
    public Optional<PermissionGroup> findGroup(String node) {
        return Optional.ofNullable(groupsByNode.get(Objects.requireNonNull(node, "node")));
    }

    /**
     * Finds a section by its organizational name relative to this namespace.
     * @param name section name relative to this namespace
     * @return the matching section, if registered
     */
    public Optional<PermissionSection> findSection(String name) {
        return Optional.ofNullable(sectionsByName.get(Objects.requireNonNull(name, "name")));
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static <T> Set<T> immutableSet(Iterable<T> values) {
        LinkedHashSet<T> copy = new LinkedHashSet<>();
        for (T value : values) {
            copy.add(value);
        }
        return Collections.unmodifiableSet(copy);
    }

    private static final class Resolver {
        private final PermissionNamespace owner;
        private final List<Permission> permissions;
        private final List<PermissionSection> sections;
        private final List<PermissionGroup> groups;
        private final Map<PermissionSection, Set<Permission>> sectionResults = new LinkedHashMap<>();
        private final Map<PermissionGroup, Set<Permission>> groupResults = new LinkedHashMap<>();
        private final IdentityHashMap<PermissionSection, VisitState> sectionVisits = new IdentityHashMap<>();
        private final IdentityHashMap<PermissionGroup, VisitState> groupVisits = new IdentityHashMap<>();

        private Resolver(PermissionNamespace owner, Iterable<Permission> permissions,
                         Iterable<PermissionSection> sections, Iterable<PermissionGroup> groups) {
            this.owner = owner;
            this.permissions = toList(permissions);
            this.sections = toList(sections);
            this.groups = toList(groups);
        }

        private Map<PermissionSection, Set<Permission>> resolveSections() {
            for (PermissionSection section : sections) {
                resolveSection(section);
            }
            return immutableMap(sectionResults);
        }

        private Map<PermissionGroup, Set<Permission>> resolveGroups() {
            for (PermissionGroup group : groups) {
                resolveGroup(group);
            }
            return immutableMap(groupResults);
        }

        private Set<Permission> resolveSection(PermissionSection section) {
            Set<Permission> cached = sectionResults.get(section);
            if (cached != null) {
                return cached;
            }
            VisitState state = sectionVisits.get(section);
            if (state == VisitState.VISITING) {
                throw new IllegalArgumentException("Circular section membership detected at '" + section.name() + "'");
            }
            if (!owner.containsSection(section)) {
                throw new IllegalArgumentException("Section references an unregistered or cross-namespace definition: " + section);
            }
            sectionVisits.put(section, VisitState.VISITING);
            LinkedHashSet<Permission> effective = new LinkedHashSet<>();
            for (PermissionMember member : section.members()) {
                if (member instanceof Permission permission) {
                    requireRegisteredPermission(permission, "section '" + section.name() + "'");
                    effective.add(permission);
                } else if (member instanceof PermissionSection child) {
                    effective.addAll(resolveSection(child));
                } else {
                    throw new IllegalArgumentException("Unsupported member in section '" + section.name() + "'");
                }
            }
            Set<Permission> result = immutableSet(effective);
            sectionResults.put(section, result);
            sectionVisits.put(section, VisitState.RESOLVED);
            return result;
        }

        private Set<Permission> resolveGroup(PermissionGroup group) {
            Set<Permission> cached = groupResults.get(group);
            if (cached != null) {
                return cached;
            }
            VisitState state = groupVisits.get(group);
            if (state == VisitState.VISITING) {
                throw new IllegalArgumentException("Circular group inheritance detected at '" + group.node() + "'");
            }
            if (!owner.containsGroup(group)) {
                throw new IllegalArgumentException("Group references an unregistered or cross-namespace definition: " + group);
            }
            groupVisits.put(group, VisitState.VISITING);

            LinkedHashSet<Permission> effective = new LinkedHashSet<>();
            for (PermissionGroup parent : group.inheritedGroups()) {
                if (!owner.containsGroup(parent)) {
                    throw new IllegalArgumentException("Group '" + group.node()
                            + "' inherits an unregistered or cross-namespace group '" + parent.node() + "'");
                }
                effective.addAll(resolveGroup(parent));
            }
            if (group.grantsAllPermissions()) {
                effective.addAll(permissions);
            }
            addMembers(effective, group.grants(), "grants of group '" + group.node() + "'");

            LinkedHashSet<Permission> excluded = new LinkedHashSet<>();
            addMembers(excluded, group.exclusions(), "exclusions of group '" + group.node() + "'");
            effective.removeAll(excluded);

            Set<Permission> result = immutableSet(effective);
            groupResults.put(group, result);
            groupVisits.put(group, VisitState.RESOLVED);
            return result;
        }

        private void addMembers(Set<Permission> target, List<PermissionMember> members, String context) {
            for (PermissionMember member : members) {
                if (member instanceof Permission permission) {
                    requireRegisteredPermission(permission, context);
                    target.add(permission);
                } else if (member instanceof PermissionSection section) {
                    target.addAll(resolveSection(section));
                } else {
                    throw new IllegalArgumentException(context + " contains an unsupported member");
                }
            }
        }

        private void requireRegisteredPermission(Permission permission, String context) {
            if (!owner.containsPermission(permission)) {
                throw new IllegalArgumentException(context + " references an unregistered or cross-namespace permission '"
                        + permission.node() + "'");
            }
        }

        private static <T> List<T> toList(Iterable<T> values) {
            List<T> result = new ArrayList<>();
            values.forEach(result::add);
            return result;
        }
    }

    private enum VisitState {
        VISITING,
        RESOLVED
    }
}
