package com.trickshotmlg.devkit.commands;

import com.trickshotmlg.devkit.permissions.Permission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Entry point for constructing and validating an immutable command tree. */
public final class CommandRegistry {
    private CommandRegistry() { }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<CommandNodeBuilder> roots = new ArrayList<>();
        private boolean built;

        /** Adds one root declaration. Configuration is completed before the root is registered. */
        public Builder root(String name, Consumer<CommandNodeBuilder> configure) {
            ensureOpen();
            Objects.requireNonNull(configure, "configure");
            CommandNodeBuilder root = new CommandNodeBuilder(CommandNodeBuilder.validateLiteral(name));
            configure.accept(root);
            roots.add(root);
            return this;
        }

        /** Validates names and argument layouts, then returns an immutable snapshot. */
        public CommandDefinition build() {
            ensureOpen();
            Map<String, CommandNode> lookup = new LinkedHashMap<>();
            List<CommandNode> immutableRoots = new ArrayList<>();
            for (CommandNodeBuilder root : roots) {
                CommandNode node = freeze(root, List.of(), List.of(), "/" + root.name());
                immutableRoots.add(node);
                for (String literal : names(root)) {
                    CommandNode previous = lookup.putIfAbsent(literal, node);
                    if (previous != null) throw duplicate("root command name or alias", literal, "/" + root.name());
                }
            }
            built = true;
            return new CommandDefinition(immutableRoots, lookup);
        }

        private void ensureOpen() { if (built) throw new IllegalStateException("Command registry builder has already been built"); }
    }

    private static CommandNode freeze(CommandNodeBuilder source, List<Permission> parentPermissions,
                                      List<SenderRequirement> parentSenderRequirements, String path) {
        validateNode(source, path);
        List<Permission> permissions = appendUnique(parentPermissions, source.permissionsInternal(), "permission requirement", path);
        List<SenderRequirement> restrictions = appendUnique(parentSenderRequirements,
                source.senderRequirementsInternal(), "sender requirement", path);
        validateSiblings(source.childrenInternal(), path);
        List<CommandNode> children = new ArrayList<>();
        for (CommandNodeBuilder child : source.childrenInternal()) {
            children.add(freeze(child, permissions, restrictions, path + " " + child.name()));
        }
        return new CommandNode(source.name(), source.aliasesInternal(), Optional.ofNullable(source.descriptionInternal()),
                Optional.ofNullable(source.usageInternal()), source.argumentsInternal(), children,
                source.permissionsInternal(), permissions, source.senderRequirementsInternal(), restrictions,
                Optional.ofNullable(source.executorInternal()));
    }

    private static void validateNode(CommandNodeBuilder node, String path) {
        Set<String> argumentNames = new LinkedHashSet<>();
        boolean optionalSeen = false;
        for (int i = 0; i < node.argumentsInternal().size(); i++) {
            ArgumentBinding<?> binding = node.argumentsInternal().get(i);
            if (!argumentNames.add(binding.argument().name())) throw duplicate("argument name", binding.argument().name(), path);
            if (!binding.required()) optionalSeen = true;
            else if (optionalSeen) throw new IllegalArgumentException("Required argument '" + binding.argument().name() + "' follows an optional argument at " + path);
            if (binding.argument().type() == ArgumentType.GREEDY_STRING && i != node.argumentsInternal().size() - 1) {
                throw new IllegalArgumentException("Greedy argument '" + binding.argument().name() + "' must be the final argument at " + path);
            }
        }
        Set<String> ownNames = new LinkedHashSet<>();
        for (String name : names(node)) if (!ownNames.add(name)) throw duplicate("command name or alias", name, path);
    }

    private static void validateSiblings(List<CommandNodeBuilder> siblings, String parentPath) {
        Map<String, String> ownerByLiteral = new LinkedHashMap<>();
        for (CommandNodeBuilder child : siblings) {
            for (String literal : names(child)) {
                String prior = ownerByLiteral.putIfAbsent(literal, child.name());
                if (prior != null) throw duplicate("sibling command name or alias", literal, parentPath);
            }
        }
    }

    private static <T> List<T> appendUnique(List<T> inherited, List<T> local, String what, String path) {
        List<T> result = new ArrayList<>(inherited);
        for (T item : local) {
            if (!result.contains(item)) result.add(item);
        }
        return List.copyOf(result);
    }

    private static List<String> names(CommandNodeBuilder node) {
        List<String> result = new ArrayList<>();
        result.add(node.name());
        result.addAll(node.aliasesInternal());
        return result;
    }

    private static IllegalArgumentException duplicate(String what, String value, String path) {
        return new IllegalArgumentException("Duplicate " + what + " '" + value + "' at " + path);
    }
}
