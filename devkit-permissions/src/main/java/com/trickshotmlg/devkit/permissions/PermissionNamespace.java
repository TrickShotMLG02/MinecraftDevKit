package com.trickshotmlg.devkit.permissions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Mutable declaration scope for one permission namespace. Calling {@link #build()} validates and
 * freezes the definitions into an immutable {@link PermissionCatalog}.
 *
 * <p>Identifiers use lowercase ASCII letters, digits, underscores, and hyphens. Dots separate
 * non-empty path segments.</p>
 */
public final class PermissionNamespace {
    private static final Pattern NAMESPACE_IDENTIFIER = Pattern.compile("[a-z0-9_-]+");
    private static final Pattern PATH_IDENTIFIER = Pattern.compile("[a-z0-9_-]+(?:\\.[a-z0-9_-]+)*");

    private final String name;
    private final Map<String, Permission> permissions = new LinkedHashMap<>();
    private final Map<String, PermissionSection> sections = new LinkedHashMap<>();
    private final Map<String, PermissionGroup> groups = new LinkedHashMap<>();
    private PermissionCatalog catalog;
    private boolean inDeclarationCallback;

    private PermissionNamespace(String name) {
        this.name = validateNamespace(name);
    }

    /**
     * Creates an independent mutable declaration scope.
     * @param name lowercase namespace identifier
     * @return new namespace scope
     * @throws IllegalArgumentException if {@code name} is not a valid identifier
     */
    public static PermissionNamespace create(String name) {
        return new PermissionNamespace(name);
    }

    /**
     * Returns this namespace's lowercase identifier.
     * @return namespace identifier
     */
    public String name() {
        return name;
    }

    /**
     * Registers an ordinary permission with default access {@link DefaultAccess#FALSE}.
     * @param path lowercase permission path relative to this namespace
     * @return immutable permission declaration
     */
    public Permission permission(String path) {
        return permission(path, options -> { });
    }

    /**
     * Registers an ordinary permission with immutable metadata copied from the callback.
     * @param path lowercase permission path relative to this namespace
     * @param configure callback for optional metadata
     * @return immutable permission declaration
     */
    public Permission permission(String path, Consumer<PermissionOptions> configure) {
        ensureMutable();
        String validPath = validatePath(path, "permission path");
        Objects.requireNonNull(configure, "configure");
        ensurePermissionNodeAvailable(validPath);

        PermissionOptions options = new PermissionOptions();
        inDeclarationCallback = true;
        try {
            configure.accept(options);
            Permission permission = new Permission(this, name, validPath,
                    options.descriptionValue(), options.defaultAccessValue());
            permissions.put(validPath, permission);
            return permission;
        } finally {
            inDeclarationCallback = false;
            options.freeze();
        }
    }

    Permission child(Permission parent, String childPath, Consumer<PermissionOptions> configure) {
        ensureMutable();
        requirePermission(parent);
        String child = validatePath(childPath, "child path");
        String path = parent.name() + "." + child;
        if (configure == null) {
            return permission(path);
        }
        return permission(path, configure);
    }

    /**
     * Registers a named organizational section. Section names never create permission nodes.
     * @param sectionName lowercase organizational name
     * @param members registered permissions and previously declared sections
     * @return immutable section declaration
     */
    public PermissionSection section(String sectionName, PermissionMember... members) {
        ensureMutable();
        String validName = validatePath(sectionName, "section name");
        Objects.requireNonNull(members, "members");
        if (sections.containsKey(validName)) {
            throw new IllegalArgumentException("Duplicate section name '" + validName + "' in namespace '" + name + "'");
        }

        List<PermissionMember> copy = new ArrayList<>(members.length);
        for (PermissionMember member : members) {
            validateMember(member, "section '" + validName + "'");
            copy.add(member);
        }
        PermissionSection section = new PermissionSection(this, name, validName, copy);
        sections.put(validName, section);
        return section;
    }

    /**
     * Declares a group atomically. The callback receives a short-lived builder; failed callbacks
     * or invalid references do not register a partial group.
     * @param groupName lowercase group path relative to this namespace
     * @param configure callback that defines grants, inheritance, exclusions, and metadata
     * @return immutable group declaration
     */
    public PermissionGroup group(String groupName, Consumer<PermissionGroup.Builder> configure) {
        ensureMutable();
        String validName = validatePath(groupName, "group name");
        Objects.requireNonNull(configure, "configure");
        ensureGroupNodeAvailable(validName);

        PermissionGroup.Builder builder = new PermissionGroup.Builder(this);
        PermissionGroup group;
        inDeclarationCallback = true;
        try {
            configure.accept(builder);
            group = builder.freeze(name, validName);
        } catch (RuntimeException | Error failure) {
            builder.freezeAfterFailure();
            throw failure;
        } finally {
            inDeclarationCallback = false;
        }

        validateGroup(group);
        groups.put(validName, group);
        return group;
    }

    /**
     * Validates all definitions and returns the immutable catalog. Repeated calls return the same
     * catalog instance. No further declarations are accepted after a successful build.
     * @return immutable validated catalog
     * @throws IllegalArgumentException if definitions contain invalid references or graph cycles
     */
    public PermissionCatalog build() {
        if (inDeclarationCallback) {
            throw new IllegalStateException("Cannot build namespace '" + name + "' inside a declaration callback");
        }
        if (catalog != null) {
            return catalog;
        }
        catalog = PermissionCatalog.create(this, permissions, sections, groups);
        return catalog;
    }

    boolean containsPermission(Permission permission) {
        return permission != null && permission.belongsTo(this)
                && permissions.get(permission.name()) == permission;
    }

    boolean containsSection(PermissionSection section) {
        return section != null && section.belongsTo(this)
                && sections.get(section.name()) == section;
    }

    boolean containsGroup(PermissionGroup group) {
        return group != null && group.belongsTo(this)
                && groups.get(group.name()) == group;
    }

    String namespaceName() {
        return name;
    }

    private void validateGroup(PermissionGroup group) {
        for (PermissionGroup inherited : group.inheritedGroups()) {
            if (!containsGroup(inherited)) {
                throw new IllegalArgumentException("Group '" + group.name()
                        + "' inherits an unknown or cross-namespace group '" + inherited.node() + "'");
            }
            if (inherited == group) {
                throw new IllegalArgumentException("Group '" + group.node() + "' cannot inherit itself");
            }
        }
        for (PermissionMember grant : group.grants()) {
            validateMember(grant, "grants of group '" + group.name() + "'");
        }
        for (PermissionMember exclusion : group.exclusions()) {
            validateMember(exclusion, "exclusions of group '" + group.name() + "'");
        }
    }

    private void validateMember(PermissionMember member, String context) {
        Objects.requireNonNull(member, context + " contains null");
        if (member instanceof Permission permission) {
            if (!containsPermission(permission)) {
                throw new IllegalArgumentException(context + " references an unregistered or cross-namespace permission '"
                        + permission.node() + "'");
            }
        } else if (member instanceof PermissionSection section) {
            if (!containsSection(section)) {
                throw new IllegalArgumentException(context + " references an unregistered or cross-namespace section '"
                        + section.name() + "'");
            }
        } else {
            throw new IllegalArgumentException(context + " contains unsupported member type '"
                    + member.getClass().getName() + "'");
        }
    }

    private void requirePermission(Permission permission) {
        if (!containsPermission(permission)) {
            throw new IllegalArgumentException("Parent permission is not registered in namespace '" + name + "'");
        }
    }

    private void ensurePermissionNodeAvailable(String path) {
        if (permissions.containsKey(path)) {
            throw new IllegalArgumentException("Duplicate permission node '" + name + "." + path + "'");
        }
        if (groups.containsKey(path)) {
            throw new IllegalArgumentException("Permission node '" + name + "." + path
                    + "' collides with a declared group");
        }
    }

    private void ensureGroupNodeAvailable(String path) {
        if (groups.containsKey(path)) {
            throw new IllegalArgumentException("Duplicate group node '" + name + "." + path + "'");
        }
        if (permissions.containsKey(path)) {
            throw new IllegalArgumentException("Group node '" + name + "." + path
                    + "' collides with a declared permission");
        }
    }

    private void ensureMutable() {
        if (catalog != null) {
            throw new IllegalStateException("Permission namespace '" + name + "' has already been built");
        }
        if (inDeclarationCallback) {
            throw new IllegalStateException("Namespace declarations cannot be nested inside a declaration callback");
        }
    }

    static String validatePath(String value, String description) {
        Objects.requireNonNull(value, description);
        if (!PATH_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + description + " '" + value
                    + "': use lowercase ASCII letters, digits, '_' or '-', separated by single dots");
        }
        return value;
    }

    private static String validateNamespace(String value) {
        Objects.requireNonNull(value, "namespace");
        if (!NAMESPACE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid namespace '" + value
                    + "': use lowercase ASCII letters, digits, '_' or '-' without dots");
        }
        return value;
    }
}
