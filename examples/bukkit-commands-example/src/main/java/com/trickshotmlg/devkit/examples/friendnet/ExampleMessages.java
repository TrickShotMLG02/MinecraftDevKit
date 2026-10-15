package com.trickshotmlg.devkit.examples.friendnet;

import com.trickshotmlg.devkit.commands.CommandContext;
import com.trickshotmlg.devkit.localization.ResourceBasedTextProvider;
import com.trickshotmlg.devkit.text.TextRef;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Runtime localized response helper; the active provider resolves for each sender's locale. */
final class ExampleMessages {
    private static final AtomicReference<ResourceBasedTextProvider> PROVIDER = new AtomicReference<>();
    private ExampleMessages() { }
    static void provider(ResourceBasedTextProvider provider) { PROVIDER.set(provider); }

    static void send(CommandContext context, String key, String fallback, Object... pairs) {
        java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) values.put((String) pairs[index], pairs[index + 1]);
        ResourceBasedTextProvider provider = java.util.Objects.requireNonNull(PROVIDER.get(), "text provider");
        context.sender().sendMessage(provider.resolve(TextRef.translated(key, fallback), context.sender().locale(),
                values, Optional.of("FriendCommands." + key)));
    }

    static void literal(CommandContext context, String message) {
        ResourceBasedTextProvider provider = java.util.Objects.requireNonNull(PROVIDER.get(), "text provider");
        context.sender().sendMessage(provider.resolve(TextRef.literal(message), context.sender().locale(), Map.of()));
    }
}
