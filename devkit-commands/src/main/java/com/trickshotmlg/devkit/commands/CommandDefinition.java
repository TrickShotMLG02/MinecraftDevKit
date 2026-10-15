package com.trickshotmlg.devkit.commands;

import com.trickshotmlg.devkit.text.TextCatalog;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable collection of validated command roots. */
public final class CommandDefinition {
    private final List<CommandNode> roots;
    private final Map<String, CommandNode> rootLookup;

    CommandDefinition(List<CommandNode> roots, Map<String, CommandNode> rootLookup) {
        this.roots = List.copyOf(roots);
        this.rootLookup = Map.copyOf(rootLookup);
    }

    public List<CommandNode> roots() { return roots; }

    /** Collects translated command descriptions, usage overrides, and argument descriptions. */
    public TextCatalog textReferences() {
        TextCatalog.Builder catalog = TextCatalog.builder();
        for (CommandNode root : roots) collect(root, catalog, "/" + root.name());
        return catalog.build();
    }

    private static void collect(CommandNode node, TextCatalog.Builder catalog, String path) {
        node.description().ifPresent(ref -> catalog.add(ref, "command-description", path));
        node.usageOverride().ifPresent(ref -> catalog.add(ref, "command-usage", path));
        for (ArgumentBinding<?> argument : node.arguments()) {
            argument.argument().description().ifPresent(ref -> catalog.add(ref, "argument-description", path + " <" + argument.argument().name() + ">"));
        }
        for (CommandNode child : node.children()) collect(child, catalog, path + " " + child.name());
    }

    /** Finds a root by its primary literal or alias. */
    public Optional<CommandNode> findRoot(String nameOrAlias) {
        Objects.requireNonNull(nameOrAlias, "nameOrAlias");
        return Optional.ofNullable(rootLookup.get(nameOrAlias));
    }
}
