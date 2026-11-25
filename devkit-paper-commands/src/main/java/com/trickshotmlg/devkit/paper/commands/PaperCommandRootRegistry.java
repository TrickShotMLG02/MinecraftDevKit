package com.trickshotmlg.devkit.paper.commands;

import com.trickshotmlg.devkit.commands.CommandDefinition;
import com.trickshotmlg.devkit.commands.CommandNode;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Prevents an adapter instance from submitting the same native root or alias twice. */
final class PaperCommandRootRegistry {
    private final Set<String> registered = new HashSet<>();

    synchronized void register(CommandDefinition definition, Runnable registration) {
        Set<String> requested = new HashSet<>();
        for (CommandNode root : definition.roots()) {
            requested.add(root.name().toLowerCase(Locale.ROOT));
            root.aliases().forEach(alias -> requested.add(alias.toLowerCase(Locale.ROOT)));
        }
        Set<String> collisions = new HashSet<>(requested);
        collisions.retainAll(registered);
        if (!collisions.isEmpty()) {
            throw new IllegalArgumentException("Command roots already registered by this Paper adapter: " + collisions);
        }
        registration.run();
        registered.addAll(requested);
    }
}
