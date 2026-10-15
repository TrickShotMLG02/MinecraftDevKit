package com.trickshotmlg.devkit.commands;

import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.text.TextRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable command node in a validated command tree. */
public final class CommandNode {
    private final String name;
    private final List<String> aliases;
    private final Optional<TextRef> description;
    private final Optional<TextRef> usageOverride;
    private final List<ArgumentBinding<?>> arguments;
    private final List<CommandNode> children;
    private final List<Permission> directPermissions;
    private final List<Permission> effectivePermissions;
    private final List<SenderRequirement> directSenderRequirements;
    private final List<SenderRequirement> effectiveSenderRequirements;
    private final Optional<CommandExecutor> executor;

    CommandNode(String name, List<String> aliases, Optional<TextRef> description,
                Optional<TextRef> usageOverride, List<ArgumentBinding<?>> arguments,
                List<CommandNode> children, List<Permission> directPermissions,
                List<Permission> effectivePermissions, List<SenderRequirement> directSenderRequirements,
                List<SenderRequirement> effectiveSenderRequirements, Optional<CommandExecutor> executor) {
        this.name = Objects.requireNonNull(name, "name");
        this.aliases = List.copyOf(aliases);
        this.description = Objects.requireNonNull(description, "description");
        this.usageOverride = Objects.requireNonNull(usageOverride, "usageOverride");
        this.arguments = List.copyOf(arguments);
        this.children = List.copyOf(children);
        this.directPermissions = List.copyOf(directPermissions);
        this.effectivePermissions = List.copyOf(effectivePermissions);
        this.directSenderRequirements = List.copyOf(directSenderRequirements);
        this.effectiveSenderRequirements = List.copyOf(effectiveSenderRequirements);
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public String name() { return name; }
    public List<String> aliases() { return aliases; }
    public Optional<TextRef> description() { return description; }
    public Optional<TextRef> usageOverride() { return usageOverride; }
    public List<ArgumentBinding<?>> arguments() { return arguments; }
    public List<CommandNode> children() { return children; }
    public List<Permission> directPermissions() { return directPermissions; }
    /** Permission requirements declared on this node and all ancestors, evaluated with logical AND. */
    public List<Permission> permissionRequirements() { return effectivePermissions; }
    public List<SenderRequirement> directSenderRequirements() { return directSenderRequirements; }
    /** Sender restrictions declared on this node and all ancestors, evaluated with logical AND. */
    public List<SenderRequirement> senderRequirements() { return effectiveSenderRequirements; }
    public Optional<CommandExecutor> executor() { return executor; }
    public boolean executable() { return executor.isPresent(); }

    /** Returns whether the supplied sender meets all inherited and local access requirements. */
    public boolean isVisibleTo(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        return effectivePermissions.stream().allMatch(sender::hasPermission)
                && effectiveSenderRequirements.stream().allMatch(requirement -> requirement.test(sender));
    }
}
