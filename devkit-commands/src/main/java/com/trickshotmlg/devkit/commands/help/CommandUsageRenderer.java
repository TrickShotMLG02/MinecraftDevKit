package com.trickshotmlg.devkit.commands.help;

import com.trickshotmlg.devkit.commands.ArgumentBinding;
import com.trickshotmlg.devkit.commands.CommandNode;
import com.trickshotmlg.devkit.commands.CommandSender;
import com.trickshotmlg.devkit.text.TextProvider;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Generates deterministic command syntaxes directly from a registered command tree. */
public final class CommandUsageRenderer {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final TextProvider textProvider;

    public CommandUsageRenderer(TextProvider textProvider) {
        this.textProvider = Objects.requireNonNull(textProvider, "textProvider");
    }

    /** Returns all syntaxes reachable from {@code root} that are visible to the sender. */
    public List<String> usages(CommandNode root, CommandSender sender) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(sender, "sender");
        List<String> result = new ArrayList<>();
        for (String rootName : rootNames(root)) {
            collect(root, "/" + rootName, sender, result);
        }
        return List.copyOf(new java.util.LinkedHashSet<>(result));
    }

    /** Returns syntaxes for one selected subtree rooted at an explicit command path. */
    public List<String> usagesAt(CommandNode selected, String commandPath, CommandSender sender) {
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(commandPath, "commandPath");
        Objects.requireNonNull(sender, "sender");
        List<String> result = new ArrayList<>();
        collect(selected, commandPath, sender, result);
        return List.copyOf(new java.util.LinkedHashSet<>(result));
    }

    /** Returns just the selected node's own usage and immediate child alternatives for compact help headers. */
    public List<String> summaryUsagesAt(CommandNode selected, String commandPath, CommandSender sender) {
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(commandPath, "commandPath");
        Objects.requireNonNull(sender, "sender");
        if (!selected.isVisibleTo(sender)) return List.of();
        List<String> result = new ArrayList<>();
        if (selected.executable() || selected.usageOverride().isPresent()) {
            result.add(selected.usageOverride().map(text -> PLAIN.serialize(
                    textProvider.resolve(text, sender.locale(), java.util.Map.of())))
                    .orElseGet(() -> syntax(commandPath, selected)));
        }
        List<CommandNode> children = selected.children().stream().filter(child -> child.isVisibleTo(sender)).toList();
        if (!children.isEmpty() && selected.arguments().isEmpty()) {
            String alternatives = children.stream().flatMap(child -> {
                List<String> names = new ArrayList<>();
                names.add(child.name());
                names.addAll(child.aliases());
                return names.stream();
            }).collect(Collectors.joining("|"));
            result.add(commandPath + " <" + alternatives + ">");
        }
        return List.copyOf(result);
    }

    private void collect(CommandNode node, String path, CommandSender sender, List<String> result) {
        if (!node.isVisibleTo(sender)) return;
        if (node.executable() || node.usageOverride().isPresent()) {
            String usage = node.usageOverride().map(text -> PLAIN.serialize(
                    textProvider.resolve(text, sender.locale(), java.util.Map.of())))
                    .orElseGet(() -> syntax(path, node));
            result.add(usage);
        }
        List<CommandNode> accessibleChildren = node.children().stream().filter(child -> child.isVisibleTo(sender)).toList();
        if (!accessibleChildren.isEmpty() && node.arguments().isEmpty()) {
            String alternatives = accessibleChildren.stream().flatMap(child -> {
                List<String> childNames = new ArrayList<>();
                childNames.add(child.name());
                childNames.addAll(child.aliases());
                return childNames.stream();
            }).collect(Collectors.joining("|"));
            result.add(path + " <" + alternatives + ">");
        }
        for (CommandNode child : node.children()) {
            List<String> childNames = new ArrayList<>();
            childNames.add(child.name());
            childNames.addAll(child.aliases());
            for (String childName : childNames) collect(child, path + " " + childName, sender, result);
        }
    }

    private static String syntax(String path, CommandNode node) {
        StringBuilder result = new StringBuilder(path);
        for (ArgumentBinding<?> binding : node.arguments()) {
            String part = "<" + binding.argument().name() + ">";
            result.append(' ').append(binding.required() ? part : "[" + binding.argument().name() + "]");
        }
        return result.toString();
    }

    private static List<String> rootNames(CommandNode root) {
        List<String> names = new ArrayList<>();
        names.add(root.name());
        names.addAll(root.aliases());
        return names;
    }
}
