package com.trickshotmlg.devkit.commands.help;

import com.trickshotmlg.devkit.commands.CommandNode;
import com.trickshotmlg.devkit.commands.CommandSender;
import com.trickshotmlg.devkit.text.TextProvider;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Renders localized help from the same command tree used for dispatch and usage. */
public final class CommandHelpRenderer {
    private final TextProvider textProvider;
    private final CommandUsageRenderer usages;

    public CommandHelpRenderer(TextProvider textProvider) {
        this.textProvider = Objects.requireNonNull(textProvider, "textProvider");
        this.usages = new CommandUsageRenderer(textProvider);
    }

    /** Returns one immutable help page. Page indices start at one. */
    public List<Component> page(CommandNode root, CommandNode selected, CommandSender sender, int page, int pageSize) {
        String defaultPath = "/" + root.name() + (selected == root ? "" : " " + selected.name());
        return page(root, selected, defaultPath, sender, page, pageSize);
    }

    /** Returns a page using the selected command's complete literal path. */
    public List<Component> page(CommandNode root, CommandNode selected, String commandPath,
                                CommandSender sender, int page, int pageSize) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(sender, "sender");
        if (page < 1) throw new IllegalArgumentException("page must be at least 1");
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be at least 1");
        if (!selected.isVisibleTo(sender)) return List.of();

        List<Component> lines = new ArrayList<>();
        selected.description().ifPresent(description -> lines.add(textProvider.resolve(description, sender.locale(), Map.of())));
        for (String usage : usages.summaryUsagesAt(selected, commandPath, sender)) lines.add(Component.text(usage));

        List<CommandNode> visibleChildren = selected.children().stream().filter(child -> child.isVisibleTo(sender)).toList();
        int totalPages = Math.max(1, (visibleChildren.size() + pageSize - 1) / pageSize);
        int selectedPage = Math.min(page, totalPages);
        int start = (selectedPage - 1) * pageSize;
        int end = Math.min(start + pageSize, visibleChildren.size());
        for (CommandNode child : visibleChildren.subList(start, end)) {
            Component line = Component.text(child.name());
            if (!child.aliases().isEmpty()) line = line.append(Component.text(" (" + String.join(", ", child.aliases()) + ")"));
            if (child.description().isPresent()) {
                line = line.append(Component.text(" — "))
                        .append(textProvider.resolve(child.description().orElseThrow(), sender.locale(), Map.of()));
            }
            lines.add(line);
        }
        if (visibleChildren.size() > pageSize) lines.add(Component.text("Page " + selectedPage + " of " + totalPages));
        return List.copyOf(lines);
    }

    /** Sends a help page to the supplied platform-neutral sender. */
    public void send(CommandNode root, CommandNode selected, CommandSender sender, int page, int pageSize) {
        page(root, selected, sender, page, pageSize).forEach(sender::sendMessage);
    }

    /** Sends one page using the selected command's complete literal path. */
    public void send(CommandNode root, CommandNode selected, String commandPath,
                     CommandSender sender, int page, int pageSize) {
        page(root, selected, commandPath, sender, page, pageSize).forEach(sender::sendMessage);
    }
}
