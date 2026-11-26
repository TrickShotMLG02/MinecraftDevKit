package com.trickshotmlg.devkit.paper.commands;

import com.mojang.brigadier.Message;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.context.StringRange;
import com.trickshotmlg.devkit.commands.CommandDefinition;
import com.trickshotmlg.devkit.commands.CommandRegistry;
import com.trickshotmlg.devkit.commands.CommandSender;
import com.trickshotmlg.devkit.commands.SenderKind;
import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.permissions.PermissionNamespace;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PaperPermissionSuggestionFilterTest {
    private final PermissionNamespace permissions = PermissionNamespace.create("devkittest");
    private final Permission inspect = permissions.permission("inspect");
    private final Permission modify = permissions.permission("modify");
    private final Permission reload = permissions.permission("admin.reload");
    private final Permission reset = permissions.permission("admin.reset");
    private final Permission regionInfo = permissions.permission("region.info");
    private final Permission regionDelete = permissions.permission("region.delete");
    private final CommandDefinition definition = CommandRegistry.builder()
            .root("devkittest", root -> root.aliases("dkt", "dktest")
                    .subcommand("public", node -> node.execute(context -> { }))
                    .subcommand("inspect", node -> node.required(com.trickshotmlg.devkit.commands.Arguments.word("target"))
                            .permission(inspect).execute(context -> { }))
                    .subcommand("modify", node -> node.required(com.trickshotmlg.devkit.commands.Arguments.word("target"))
                            .permission(modify).execute(context -> { }))
                    .subcommand("admin", node -> node
                            .subcommand("reload", child -> child.permission(reload).execute(context -> { }))
                            .subcommand("reset", child -> child.permission(reset).execute(context -> { })))
                    .subcommand("region", node -> node
                            .subcommand("info", child -> child.permission(regionInfo).execute(context -> { }))
                            .subcommand("delete", child -> child.permission(regionDelete).execute(context -> { }))))
            .build();

    @Test void filtersDeniedRootAndNestedLiteralsButKeepsAccessibleIntermediateNodes() {
        Map<String, com.trickshotmlg.devkit.commands.CommandNode> roots = rootMap();
        Suggestions root = suggestions(12, "public", "inspect", "modify", "admin", "region", "other");
        Suggestions filteredRoot = filter("/dkt ", root, roots, sender(Set.of()));
        assertEquals(List.of("public", "other"), texts(filteredRoot));
        assertEquals(List.of("public", "inspect", "admin", "region", "other"),
                texts(filter("/dkt ", root, roots, sender(Set.of("devkittest.inspect", "devkittest.admin.reload", "devkittest.region.info")))));
        assertSame(root.getList().get(0), filteredRoot.getList().get(0));

        Suggestions nested = suggestions(19, "info", "delete", "foreign");
        assertEquals(List.of("foreign"), texts(filter("/devkittest region ", nested, roots, sender(Set.of()))));
        assertEquals(List.of("info", "foreign"), texts(filter("/devkittest region ", nested, roots, sender(Set.of("devkittest.region.info")))));
    }

    @Test void evaluatesCurrentPermissionStateIncludingGrantRevocationAndExplicitDenial() {
        Map<String, com.trickshotmlg.devkit.commands.CommandNode> roots = rootMap();
        Suggestions original = suggestions(18, "reload", "reset");
        MutableSender sender = new MutableSender();
        assertEquals(List.of(), texts(filter("/devkittest admin ", original, roots, sender)));
        sender.grants.add("devkittest.admin.*");
        sender.denials.add("devkittest.admin.reset");
        assertEquals(List.of("reload"), texts(filter("/devkittest admin ", original, roots, sender)));
        sender.denials.clear();
        assertEquals(List.of("reload", "reset"), texts(filter("/devkittest admin ", original, roots, sender)));
        sender.grants.clear();
        assertEquals(List.of(), texts(filter("/devkittest admin ", original, roots, sender)));
    }

    @Test void preservesAliasesNamespaceRootsUnownedRootsAndForeignSuggestions() {
        Map<String, com.trickshotmlg.devkit.commands.CommandNode> roots = rootMap();
        Suggestions input = suggestions(12, "inspect", "public", "external:command");
        assertEquals(List.of("public", "external:command"), texts(filter("/devkittest ", input, roots, sender(Set.of()))));
        assertEquals(List.of("public", "external:command"), texts(filter("/mdkplugin:dkt ", suggestions(15,
                "inspect", "public", "external:command"), roots, sender(Set.of()), "mdkplugin")));
        assertSame(input, filter("/otherroot ", input, roots, sender(Set.of())));
        assertSame(input, filter("/devkittest ", input, Map.of(), sender(Set.of())));
    }

    @Test void preservesArgumentSuggestionsAndAmbiguousArgumentLiteralText() {
        Map<String, com.trickshotmlg.devkit.commands.CommandNode> roots = rootMap();
        Suggestions dynamic = suggestions(20, "private-target", "modify", "inspect");
        assertSame(dynamic, filter("/devkittest inspect ", dynamic, roots, sender(Set.of())));
        // The already typed inspect token can also be parsed as a value if a schema has an argument
        // alongside a literal branch. The filter conservatively leaves such paths unchanged.
        Suggestions ambiguous = suggestions(20, "modify", "public");
        assertSame(ambiguous, filter("/devkittest inspect ", ambiguous, roots, sender(Set.of())));
    }

    @Test void preservesSuggestionRangeTooltipOrderAndDuplicateTextWhenFilteringMixedResults() {
        StringRange range = StringRange.between(12, 13);
        Message tooltip = () -> "hint";
        Suggestion allowed = new Suggestion(range, "public", tooltip);
        Suggestion denied = new Suggestion(range, "inspect", tooltip);
        Suggestion duplicateAllowed = new Suggestion(range, "public", tooltip);
        Suggestions original = new Suggestions(range, List.of(allowed, denied, duplicateAllowed));

        Suggestions filtered = filter("/devkittest p", original, rootMap(), sender(Set.of()));
        assertEquals(List.of("public", "public"), texts(filtered));
        assertEquals(range, filtered.getRange());
        assertSame(allowed, filtered.getList().get(0));
        assertSame(duplicateAllowed, filtered.getList().get(1));
        assertEquals("hint", filtered.getList().get(0).getTooltip().getString());
    }

    @Test void leavesSuggestionListAloneWhenAllSuggestionsAreUnknownOrNoKnownRootMatches() {
        Suggestions original = suggestions(4, "world-alpha", "server-command");
        assertSame(original, filter("/devkittest ", original, rootMap(), sender(Set.of())));
        assertSame(original, filter("/some-other-root ", original, rootMap(), sender(Set.of())));
    }

    private Suggestions filter(String buffer, Suggestions suggestions,
                               Map<String, com.trickshotmlg.devkit.commands.CommandNode> roots,
                               CommandSender sender) {
        return filter(buffer, suggestions, roots, sender, "mdkplugin");
    }

    private Suggestions filter(String buffer, Suggestions suggestions,
                               Map<String, com.trickshotmlg.devkit.commands.CommandNode> roots,
                               CommandSender sender, String pluginName) {
        return PaperPermissionSuggestionFilter.filterSuggestions(buffer, suggestions, roots, pluginName, sender);
    }

    private Map<String, com.trickshotmlg.devkit.commands.CommandNode> rootMap() {
        Map<String, com.trickshotmlg.devkit.commands.CommandNode> roots = new HashMap<>();
        var root = definition.roots().get(0);
        roots.put("devkittest", root);
        roots.put("dkt", root);
        roots.put("dktest", root);
        return roots;
    }

    private static Suggestions suggestions(int cursor, String... values) {
        StringRange range = StringRange.at(cursor);
        return new Suggestions(range, java.util.Arrays.stream(values).map(value -> new Suggestion(range, value)).toList());
    }

    private static List<String> texts(Suggestions suggestions) {
        return suggestions.getList().stream().map(Suggestion::getText).toList();
    }

    private static CommandSender sender(Set<String> grants) { return new MutableSender(grants); }

    private static final class MutableSender implements CommandSender {
        private final Set<String> grants = new java.util.HashSet<>();
        private final Set<String> denials = new java.util.HashSet<>();
        private MutableSender() { }
        private MutableSender(Set<String> grants) { this.grants.addAll(grants); }
        @Override public SenderKind kind() { return SenderKind.PLAYER; }
        @Override public Locale locale() { return Locale.ENGLISH; }
        @Override public boolean hasPermission(Permission permission) {
            String node = permission.node();
            if (denials.contains(node)) return false;
            if (grants.contains(node)) return true;
            int end = node.lastIndexOf('.');
            while (end > 0) {
                if (grants.contains(node.substring(0, end) + ".*")) return true;
                end = node.lastIndexOf('.', end - 1);
            }
            return grants.contains("*");
        }
        @Override public void sendMessage(Component message) { }
    }
}
