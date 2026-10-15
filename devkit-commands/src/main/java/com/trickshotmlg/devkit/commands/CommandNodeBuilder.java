package com.trickshotmlg.devkit.commands;

import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.text.TextRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Mutable, short-lived builder used to declare one command node. */
public final class CommandNodeBuilder {
    private final String name;
    private final List<String> aliases = new ArrayList<>();
    private final List<ArgumentBinding<?>> arguments = new ArrayList<>();
    private final List<CommandNodeBuilder> children = new ArrayList<>();
    private final List<Permission> permissions = new ArrayList<>();
    private final List<SenderRequirement> senderRequirements = new ArrayList<>();
    private TextRef description;
    private TextRef usage;
    private CommandExecutor executor;

    CommandNodeBuilder(String name) { this.name = name; }

    public CommandNodeBuilder aliases(String... values) {
        Objects.requireNonNull(values, "values");
        for (String value : values) aliases.add(validateLiteral(value));
        return this;
    }

    public CommandNodeBuilder description(String value) { return description(TextRef.literal(value)); }
    public CommandNodeBuilder description(TextRef value) { description = Objects.requireNonNull(value, "description"); return this; }
    public CommandNodeBuilder usage(String value) { return usage(TextRef.literal(value)); }
    public CommandNodeBuilder usage(TextRef value) { usage = Objects.requireNonNull(value, "usage"); return this; }

    public <T> CommandNodeBuilder required(Argument<T> argument) {
        arguments.add(new ArgumentBinding<>(Objects.requireNonNull(argument, "argument"), true, Optional.empty()));
        return this;
    }
    public <T> CommandNodeBuilder optional(Argument<T> argument) {
        arguments.add(new ArgumentBinding<>(Objects.requireNonNull(argument, "argument"), false, Optional.empty()));
        return this;
    }
    public <T> CommandNodeBuilder optional(Argument<T> argument, T defaultValue) {
        arguments.add(new ArgumentBinding<>(Objects.requireNonNull(argument, "argument"), false,
                Optional.of(Objects.requireNonNull(defaultValue, "defaultValue"))));
        return this;
    }

    public CommandNodeBuilder permission(Permission permission) { permissions.add(Objects.requireNonNull(permission, "permission")); return this; }
    public CommandNodeBuilder playerOnly() { return senderRequirement(SenderRequirement.player()); }
    public CommandNodeBuilder consoleOnly() { return senderRequirement(SenderRequirement.console()); }
    public CommandNodeBuilder senderRequirement(SenderRequirement requirement) { senderRequirements.add(Objects.requireNonNull(requirement, "requirement")); return this; }

    public CommandNodeBuilder execute(CommandExecutor handler) { executor = Objects.requireNonNull(handler, "handler"); return this; }

    public CommandNodeBuilder subcommand(String childName, Consumer<CommandNodeBuilder> configure) {
        Objects.requireNonNull(configure, "configure");
        CommandNodeBuilder child = new CommandNodeBuilder(validateLiteral(childName));
        configure.accept(child);
        children.add(child);
        return this;
    }

    String name() { return name; }
    List<String> aliasesInternal() { return aliases; }
    List<ArgumentBinding<?>> argumentsInternal() { return arguments; }
    List<CommandNodeBuilder> childrenInternal() { return children; }
    List<Permission> permissionsInternal() { return permissions; }
    List<SenderRequirement> senderRequirementsInternal() { return senderRequirements; }
    TextRef descriptionInternal() { return description; }
    TextRef usageInternal() { return usage; }
    CommandExecutor executorInternal() { return executor; }

    static String validateLiteral(String literal) {
        Objects.requireNonNull(literal, "literal");
        if (!literal.matches("[a-z0-9][a-z0-9_-]*")) {
            throw new IllegalArgumentException("Command literals must be lowercase and match [a-z0-9][a-z0-9_-]*: " + literal);
        }
        return literal;
    }
}
