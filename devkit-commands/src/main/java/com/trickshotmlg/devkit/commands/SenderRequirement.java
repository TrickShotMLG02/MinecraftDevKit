package com.trickshotmlg.devkit.commands;

import java.util.Objects;
import java.util.function.Predicate;

/** A platform-neutral sender restriction that an adapter can evaluate consistently. */
public sealed interface SenderRequirement permits SenderRequirement.Kind, SenderRequirement.Custom {
    boolean test(CommandSender sender);
    String description();

    static SenderRequirement player() { return new Kind(SenderKind.PLAYER); }
    static SenderRequirement console() { return new Kind(SenderKind.CONSOLE); }
    static SenderRequirement custom(String description, Predicate<CommandSender> predicate) {
        return new Custom(description, predicate);
    }

    record Kind(SenderKind expected) implements SenderRequirement {
        public Kind { Objects.requireNonNull(expected, "expected"); if (expected == SenderKind.OTHER) throw new IllegalArgumentException("OTHER is not a supported built-in restriction"); }
        @Override public boolean test(CommandSender sender) { return sender.kind() == expected; }
        @Override public String description() { return expected == SenderKind.PLAYER ? "player-only" : "console-only"; }
    }

    final class Custom implements SenderRequirement {
        private final String description;
        private final Predicate<CommandSender> predicate;
        private Custom(String description, Predicate<CommandSender> predicate) {
            this.description = Objects.requireNonNull(description, "description").trim();
            if (this.description.isEmpty()) throw new IllegalArgumentException("description must not be blank");
            this.predicate = Objects.requireNonNull(predicate, "predicate");
        }
        @Override public boolean test(CommandSender sender) { return predicate.test(sender); }
        @Override public String description() { return description; }
    }
}
