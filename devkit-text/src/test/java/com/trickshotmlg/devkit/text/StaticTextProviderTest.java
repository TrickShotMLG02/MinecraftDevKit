package com.trickshotmlg.devkit.text;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StaticTextProviderTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void resolvesLiteralText() {
        StaticTextProvider provider = new StaticTextProvider();

        assertEquals("Manage your friends", PLAIN.serialize(provider.resolve(
                TextRef.literal("Manage your friends"), Locale.US)));
    }

    @Test
    void fallbackIsUsedForTranslatedReference() {
        StaticTextProvider provider = new StaticTextProvider();

        assertEquals("Manage your friends", PLAIN.serialize(provider.resolve(
                TextRef.translated("commands.friends.description", "Manage your friends"), Locale.GERMANY)));
    }

    @Test
    void unresolvedKeyUsesConfiguredPolicy() {
        assertEquals("commands.friends.description", PLAIN.serialize(new StaticTextProvider().resolve(
                TextRef.translated("commands.friends.description"), Locale.US)));
        assertEquals("<missing:commands.friends.description>", PLAIN.serialize(
                new StaticTextProvider(MissingTranslationPolicy.SHOW_MARKER).resolve(
                        TextRef.translated("commands.friends.description"), Locale.US)));
        MissingTranslationException exception = assertThrows(MissingTranslationException.class,
                () -> new StaticTextProvider(MissingTranslationPolicy.ERROR).resolve(
                        TextRef.translated("commands.friends.description"), Locale.GERMANY));
        assertEquals("commands.friends.description", exception.key());
        assertEquals(Locale.GERMANY, exception.locale());
    }

    @Test
    void appliesNamedPlaceholdersAsPlainText() {
        StaticTextProvider provider = new StaticTextProvider();

        assertEquals("Added <player>.", PLAIN.serialize(provider.resolve(
                TextRef.literal("Added {player}."), Locale.US, Map.of("player", "<player>"))));
    }

    @Test
    void doubledBracesEscapePlaceholderSyntax() {
        StaticTextProvider provider = new StaticTextProvider();

        assertEquals("Use {name} literally", PLAIN.serialize(provider.resolve(
                TextRef.literal("Use {{name}} literally"), Locale.US, Map.of("name", "ignored"))));
    }

    @Test
    void unknownPlaceholdersRemainVisible() {
        StaticTextProvider provider = new StaticTextProvider();

        assertEquals("Hello {player}", PLAIN.serialize(provider.resolve(
                TextRef.literal("Hello {player}"), Locale.US, Map.of())));
    }

    @Test
    void rejectsNullResolutionInputsAndPlaceholderValues() {
        StaticTextProvider provider = new StaticTextProvider();
        assertThrows(NullPointerException.class, () -> provider.resolve(null, Locale.US));
        assertThrows(NullPointerException.class, () -> provider.resolve(TextRef.literal("x"), null));
        Map<String, Object> nullValue = new HashMap<>();
        nullValue.put("name", null);
        assertThrows(NullPointerException.class, () -> provider.resolve(TextRef.literal("{name}"), Locale.US, nullValue));
        assertThrows(IllegalArgumentException.class, () -> provider.resolve(
                TextRef.literal("text"), Locale.US, Map.of("bad name", "value")));
    }
}
