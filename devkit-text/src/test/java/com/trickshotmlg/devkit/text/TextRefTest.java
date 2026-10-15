package com.trickshotmlg.devkit.text;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextRefTest {
    @Test
    void literalIsImmutableTextWithoutTranslationMetadata() {
        TextRef reference = TextRef.literal("Manage your friends");

        assertEquals("Manage your friends", ((TextRef.Literal) reference).value());
        assertEquals(Optional.empty(), reference.translationKey());
        assertEquals(Optional.empty(), reference.fallback());
    }

    @Test
    void translatedReferenceAllowsMissingFallback() {
        TextRef reference = TextRef.translated("commands.friends.description");

        assertEquals(Optional.of("commands.friends.description"), reference.translationKey());
        assertTrue(reference.fallback().isEmpty());
    }

    @Test
    void translatedReferenceStoresOptionalFallback() {
        TextRef reference = TextRef.translated("commands.friends.description", "Manage your friends");

        assertEquals(Optional.of("Manage your friends"), reference.fallback());
    }

    @Test
    void invalidTranslationKeysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> TextRef.translated(""));
        assertThrows(IllegalArgumentException.class, () -> TextRef.translated("commands friends"));
        assertThrows(NullPointerException.class, () -> TextRef.translated(null));
    }

    @Test
    void nullValuesAreRejected() {
        assertThrows(NullPointerException.class, () -> TextRef.literal(null));
        assertThrows(NullPointerException.class, () -> TextRef.translated("key", (String) null));
    }

    @Test
    void referencesHaveValueEquality() {
        assertEquals(TextRef.translated("friends.add", "Add friend"),
                TextRef.translated("friends.add", "Add friend"));
        assertFalse(TextRef.literal("one").equals(TextRef.literal("two")));
    }
}
