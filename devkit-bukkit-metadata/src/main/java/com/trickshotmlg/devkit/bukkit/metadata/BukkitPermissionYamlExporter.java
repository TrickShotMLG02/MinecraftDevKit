package com.trickshotmlg.devkit.bukkit.metadata;

import com.trickshotmlg.devkit.permissions.DefaultAccess;
import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.permissions.PermissionCatalog;
import com.trickshotmlg.devkit.permissions.PermissionGroup;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Exports a permission catalog as a Bukkit-compatible YAML {@code permissions} section.
 *
 * <p>Each ordinary permission and group is emitted as a top-level permission declaration.
 * Group inheritance is flattened using the catalog's resolved effective grants; exclusions are
 * represented by omitting the excluded child. No false child assignments are generated.</p>
 *
 * <p>This is metadata export only. Bukkit child permissions create native assignments and can
 * interact with defaults, attachments, other native permission declarations, and external
 * permission managers. Flattening preserves the catalog's declared group grant sets but cannot
 * guarantee identical authorization results in every server permission setup.</p>
 */
public final class BukkitPermissionYamlExporter {
    private BukkitPermissionYamlExporter() {
    }

    /**
     * Serializes all ordinary permission and group definitions in deterministic catalog order.
     * Sections are organizational and are not emitted as permission nodes. The result is a YAML
     * document whose root contains {@code permissions:}; it can be copied into or incorporated by
     * a build tool into a plugin descriptor.
     *
     * @param catalog immutable catalog to export
     * @return YAML document containing the catalog's permission declarations
     * @throws NullPointerException if {@code catalog} is null
     */
    public static String export(PermissionCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");

        Map<String, Object> permissions = new LinkedHashMap<>();
        for (Permission permission : catalog.permissions()) {
            permissions.put(permission.node(), metadata(permission.description(), permission.defaultAccess()));
        }
        for (PermissionGroup group : catalog.groups()) {
            Map<String, Object> definition = metadata(group.description(), group.defaultAccess());
            Map<String, Boolean> children = new LinkedHashMap<>();
            for (Permission permission : catalog.effectivePermissions(group)) {
                children.put(permission.node(), Boolean.TRUE);
            }
            if (!children.isEmpty()) {
                definition.put("children", children);
            }
            permissions.put(group.node(), definition);
        }

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("permissions", permissions);
        return new Yaml(dumperOptions()).dump(document);
    }

    private static Map<String, Object> metadata(String description, DefaultAccess defaultAccess) {
        Map<String, Object> definition = new LinkedHashMap<>();
        if (description != null) {
            definition.put("description", description);
        }
        definition.put("default", bukkitDefault(defaultAccess));
        return definition;
    }

    private static String bukkitDefault(DefaultAccess defaultAccess) {
        return switch (defaultAccess) {
            case TRUE -> "true";
            case FALSE -> "false";
            case OP -> "op";
            case NOT_OP -> "not op";
        };
    }

    private static DumperOptions dumperOptions() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        options.setPrettyFlow(false);
        options.setSplitLines(false);
        options.setAllowUnicode(true);
        options.setLineBreak(DumperOptions.LineBreak.UNIX);
        return options;
    }
}
