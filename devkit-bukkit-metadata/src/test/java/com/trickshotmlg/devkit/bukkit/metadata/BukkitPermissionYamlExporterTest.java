package com.trickshotmlg.devkit.bukkit.metadata;

import com.trickshotmlg.devkit.permissions.DefaultAccess;
import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.permissions.PermissionCatalog;
import com.trickshotmlg.devkit.permissions.PermissionGroup;
import com.trickshotmlg.devkit.permissions.PermissionNamespace;
import com.trickshotmlg.devkit.permissions.PermissionSection;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitPermissionYamlExporterTest {
    @Test
    void exportsOrdinaryPermissionsDescriptionsAndAllDefaultAccessValues() {
        PermissionNamespace namespace = PermissionNamespace.create("metadata");
        Permission everyone = namespace.permission("everyone", options -> options
                .description("Available to everyone")
                .defaultAccess(DefaultAccess.TRUE));
        Permission nobody = namespace.permission("nobody", options -> options
                .description("Not granted by default")
                .defaultAccess(DefaultAccess.FALSE));
        Permission operators = namespace.permission("operators", options -> options
                .description("Operator access")
                .defaultAccess(DefaultAccess.OP));
        Permission nonOperators = namespace.permission("non-operators", options -> options
                .description("Non-operator access")
                .defaultAccess(DefaultAccess.NOT_OP));
        Permission yamlBooleanWord = namespace.permission("yaml-boolean-word", options -> options
                .description("true"));
        PermissionCatalog catalog = namespace.build();

        Map<String, Object> exported = permissions(BukkitPermissionYamlExporter.export(catalog));

        assertDefinition(exported, everyone.node(), "Available to everyone", "true");
        assertDefinition(exported, nobody.node(), "Not granted by default", "false");
        assertDefinition(exported, operators.node(), "Operator access", "op");
        assertDefinition(exported, nonOperators.node(), "Non-operator access", "not op");
        assertEquals("true", definition(exported, yamlBooleanWord.node()).get("description"));
    }

    @Test
    void exportsGroupMetadataAndFlattensNestedSectionsAndMultipleInheritance() {
        PermissionNamespace namespace = PermissionNamespace.create("shop");
        Permission view = namespace.permission("shop.view");
        Permission buy = namespace.permission("shop.buy");
        Permission refund = namespace.permission("shop.refund");
        PermissionSection basic = namespace.section("basic", view, buy);
        PermissionSection nested = namespace.section("nested", basic, refund);
        PermissionGroup member = namespace.group("member", group -> group
                .description("Basic shop access")
                .defaultAccess(DefaultAccess.TRUE)
                .grant(basic));
        PermissionGroup staff = namespace.group("staff", group -> group
                .description("Shop staff")
                .defaultAccess(DefaultAccess.OP)
                .grant(refund));
        PermissionGroup manager = namespace.group("manager", group -> group
                .defaultAccess(DefaultAccess.FALSE)
                .inherits(member, staff)
                .grant(nested));
        PermissionCatalog catalog = namespace.build();

        Map<String, Object> exported = permissions(BukkitPermissionYamlExporter.export(catalog));

        assertDefinition(exported, member.node(), "Basic shop access", "true");
        assertDefinition(exported, staff.node(), "Shop staff", "op");
        assertDefinition(exported, manager.node(), null, "false");
        assertEquals(List.of(view.node(), buy.node(), refund.node()),
                new ArrayList<>(children(exported, manager.node()).keySet()));
        assertFalse(exported.containsKey("shop.basic"));
        assertFalse(exported.containsKey("shop.nested"));
    }

    @Test
    void exclusionsOmitChildrenEvenAcrossOverlappingGrantPathsAndNeverEmitNegativeValues() {
        PermissionNamespace namespace = PermissionNamespace.create("overlap");
        Permission shared = namespace.permission("shared");
        Permission leftOnly = namespace.permission("left");
        Permission rightOnly = namespace.permission("right");
        PermissionSection overlap = namespace.section("overlap", shared, rightOnly);
        PermissionGroup left = namespace.group("left-role", group -> group.grant(shared, leftOnly));
        PermissionGroup right = namespace.group("right-role", group -> group.grant(overlap));
        PermissionGroup combined = namespace.group("combined", group -> group
                .inherits(left, right)
                .grant(shared)
                .exclude(shared));
        PermissionCatalog catalog = namespace.build();

        Map<String, Object> exported = permissions(BukkitPermissionYamlExporter.export(catalog));
        Map<String, Boolean> flattened = children(exported, combined.node());

        assertEquals(Set.of(leftOnly.node(), rightOnly.node()), flattened.keySet());
        assertTrue(flattened.values().stream().allMatch(Boolean.TRUE::equals));
        assertFalse(flattened.containsKey(shared.node()));
        for (PermissionGroup group : catalog.groups()) {
            assertTrue(children(exported, group.node()).values().stream().allMatch(Boolean.TRUE::equals));
        }
    }

    @Test
    void allPermissionsIncludesLateOrdinaryDeclarationsButNotGroupsAndEmptyGroupsOmitChildren() {
        PermissionNamespace namespace = PermissionNamespace.create("all");
        Permission first = namespace.permission("first");
        PermissionGroup all = namespace.group("admin", group -> group
                .defaultAccess(DefaultAccess.OP)
                .grantAllPermissions());
        Permission late = namespace.permission("late");
        PermissionGroup empty = namespace.group("empty", group -> { });
        PermissionCatalog catalog = namespace.build();

        Map<String, Object> exported = permissions(BukkitPermissionYamlExporter.export(catalog));

        assertEquals(List.of(first.node(), late.node()), new ArrayList<>(children(exported, all.node()).keySet()));
        assertFalse(children(exported, all.node()).containsKey(empty.node()));
        assertFalse(definition(exported, empty.node()).containsKey("children"));
        assertFalse(definition(exported, first.node()).containsKey("children"));
    }

    @Test
    void outputIsDeterministicAndPreservesCatalogOrdering() {
        PermissionNamespace namespace = PermissionNamespace.create("ordered");
        namespace.permission("second");
        namespace.permission("first");
        namespace.group("z-group", group -> { });
        namespace.group("a-group", group -> { });
        PermissionCatalog catalog = namespace.build();

        String first = BukkitPermissionYamlExporter.export(catalog);
        String second = BukkitPermissionYamlExporter.export(catalog);

        assertEquals(first, second);
        Map<String, Object> exported = permissions(first);
        assertEquals(List.of("ordered.second", "ordered.first", "ordered.z-group", "ordered.a-group"),
                new ArrayList<>(exported.keySet()));
    }

    @Test
    void yamlEscapesDescriptionsAndRoundTripsThemExactly() {
        String description = "Quotes: \"yes\"; colon: value # comment\nsecond line with café and 雪";
        PermissionNamespace namespace = PermissionNamespace.create("escaping");
        Permission permission = namespace.permission("message", options -> options.description(description));
        String yaml = BukkitPermissionYamlExporter.export(namespace.build());

        assertEquals(description, definition(permissions(yaml), permission.node()).get("description"));
    }

    @Test
    void emptyCatalogProducesAValidEmptyPermissionsMappingAndRejectsNull() {
        PermissionCatalog catalog = PermissionNamespace.create("empty").build();

        assertTrue(permissions(BukkitPermissionYamlExporter.export(catalog)).isEmpty());
        assertThrows(NullPointerException.class, () -> BukkitPermissionYamlExporter.export(null));
    }

    @Test
    void friendNetCatalogExportsAllDefinitionsAndResolvedGroups() {
        FriendNet fixture = new FriendNet();
        Map<String, Object> exported = permissions(BukkitPermissionYamlExporter.export(fixture.catalog));

        assertEquals(fixture.catalog.permissions().size() + fixture.catalog.groups().size(), exported.size());
        assertFalse(exported.containsKey("friendnet.basic"));
        assertFalse(exported.containsKey("friendnet.friends.basic"));

        assertTrue(children(exported, fixture.defaultGroup.node()).containsKey(fixture.friendList.node()));
        assertFalse(children(exported, fixture.defaultGroup.node()).containsKey(fixture.friendListOthers.node()));
        assertTrue(children(exported, fixture.extendedGroup.node()).containsKey(fixture.friendListOthers.node()));
        assertTrue(children(exported, fixture.extendedGroup.node()).containsKey(fixture.requestAcceptAll.node()));
        assertFalse(children(exported, fixture.moderator.node()).containsKey(fixture.requestAcceptAll.node()));
        assertTrue(children(exported, fixture.admin.node()).containsKey(fixture.requestAcceptAll.node()));
        assertFalse(children(exported, fixture.admin.node()).containsKey(fixture.proxyHandshake.node()));
        assertEquals("op", definition(exported, fixture.admin.node()).get("default"));
        assertFalse(children(exported, fixture.moderator.node()).containsKey(fixture.defaultGroup.node()));
        assertTrue(exported.values().stream().map(value -> castMap(value, "definition"))
                .flatMap(value -> value.containsKey("children")
                        ? castMap(value.get("children"), "children").values().stream()
                        : java.util.stream.Stream.empty())
                .allMatch(Boolean.TRUE::equals));
    }

    private static void assertDefinition(Map<String, Object> permissions, String node,
                                         String description, String defaultValue) {
        Map<String, Object> definition = definition(permissions, node);
        if (description != null) {
            assertEquals(description, definition.get("description"));
        }
        assertEquals(defaultValue, definition.get("default"));
    }

    private static Map<String, Object> permissions(String yamlText) {
        Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yamlText);
        assertNotNull(loaded);
        Map<String, Object> root = castMap(loaded, "root");
        return castMap(root.get("permissions"), "permissions");
    }

    private static Map<String, Object> definition(Map<String, Object> permissions, String node) {
        return castMap(permissions.get(node), "definition for " + node);
    }

    private static Map<String, Boolean> children(Map<String, Object> permissions, String groupNode) {
        Object children = definition(permissions, groupNode).get("children");
        if (children == null) {
            return Map.of();
        }
        return castMap(children, "children for " + groupNode);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> castMap(Object value, String description) {
        assertTrue(value instanceof Map<?, ?>, description + " must be a mapping");
        return (Map<K, V>) value;
    }

    private static final class FriendNet {
        private final PermissionNamespace namespace = PermissionNamespace.create("friendnet");
        private final Permission friendList = namespace.permission("friends.list");
        private final Permission friendListOthers = friendList.child("others");
        private final Permission friendAdd = namespace.permission("friends.add");
        private final Permission friendRemove = namespace.permission("friends.remove");
        private final Permission friendBlock = namespace.permission("friends.block");
        private final Permission requestList = namespace.permission("friends.requests.list");
        private final Permission requestAccept = namespace.permission("friends.requests.accept");
        private final Permission requestAcceptAll = requestAccept.child("all");
        private final Permission reload = namespace.permission("reload");
        private final Permission proxyHandshake = namespace.permission("proxy.handshake");
        private final PermissionSection friendsBasic = namespace.section("friends.basic",
                friendList, friendAdd, friendRemove);
        private final PermissionSection requestsBasic = namespace.section("requests.basic",
                requestList, requestAccept);
        private final PermissionSection basic = namespace.section("basic", friendsBasic, requestsBasic);
        private final PermissionSection extended = namespace.section("extended",
                friendListOthers, requestAcceptAll);
        private final PermissionSection staff = namespace.section("staff", friendBlock, reload);
        private final PermissionGroup defaultGroup = namespace.group("default", group -> group
                .defaultAccess(DefaultAccess.TRUE).grant(basic));
        private final PermissionGroup extendedGroup = namespace.group("default.extended", group -> group
                .inherits(defaultGroup).grant(extended));
        private final PermissionGroup moderator = namespace.group("moderator", group -> group
                .inherits(extendedGroup).grant(staff).exclude(requestAcceptAll));
        private final PermissionGroup admin = namespace.group("admin", group -> group
                .defaultAccess(DefaultAccess.OP).grantAllPermissions().exclude(proxyHandshake));
        private final PermissionCatalog catalog = namespace.build();
    }
}
