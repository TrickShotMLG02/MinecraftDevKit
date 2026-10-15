package com.trickshotmlg.devkit.commands;

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

    /** Finds a root by its primary literal or alias. */
    public Optional<CommandNode> findRoot(String nameOrAlias) {
        Objects.requireNonNull(nameOrAlias, "nameOrAlias");
        return Optional.ofNullable(rootLookup.get(nameOrAlias));
    }
}
