package com.trickshotmlg.devkit.localization;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocaleFallbackResolverTest {
    @Test
    void canonicalizesTagsAndBuildsRegionalParentChains() {
        assertEquals(List.of(Locale.forLanguageTag("en-GB"), Locale.ENGLISH),
                LocaleFallbackResolver.parents(Locale.forLanguageTag("en-gb")));
        assertEquals(List.of(Locale.forLanguageTag("de-AT"), Locale.GERMAN),
                LocaleFallbackResolver.parents(Locale.forLanguageTag("de-at")));
        assertEquals(List.of(Locale.forLanguageTag("pt-BR"), Locale.forLanguageTag("pt")),
                LocaleFallbackResolver.parents(Locale.forLanguageTag("pt-br")));
    }

    @Test
    void scriptRegionAndVariantAreRemovedInBcp47RightToLeftOrder() {
        assertEquals(List.of(Locale.forLanguageTag("zh-Hant-TW"), Locale.forLanguageTag("zh-Hant"),
                        Locale.forLanguageTag("zh")),
                LocaleFallbackResolver.parents(Locale.forLanguageTag("zh-Hant-TW")));
        assertEquals(List.of(Locale.forLanguageTag("sl-Latn-SI-rozaj"),
                        Locale.forLanguageTag("sl-Latn-SI"), Locale.forLanguageTag("sl-Latn"),
                        Locale.forLanguageTag("sl")),
                LocaleFallbackResolver.parents(Locale.forLanguageTag("sl-Latn-SI-rozaj")));
    }

    @Test
    void configuredFallbackChainIsOptionalAndDeduplicated() {
        Locale requested = Locale.forLanguageTag("en-GB");
        assertEquals(List.of(Locale.forLanguageTag("en-GB"), Locale.ENGLISH,
                        Locale.forLanguageTag("de-AT"), Locale.GERMAN),
                LocaleFallbackResolver.chain(requested, Locale.forLanguageTag("de-AT"), true));
        assertEquals(List.of(Locale.forLanguageTag("en-GB"), Locale.ENGLISH),
                LocaleFallbackResolver.chain(requested, Locale.ENGLISH, true));
        assertEquals(List.of(Locale.forLanguageTag("en-GB"), Locale.ENGLISH),
                LocaleFallbackResolver.chain(requested, Locale.GERMAN, false));
    }

    @Test
    void malformedOrLanguageLessTagsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> LocaleFallbackResolver.parse("not_a_locale"));
        assertThrows(IllegalArgumentException.class, () -> LocaleFallbackResolver.normalize(Locale.ROOT));
    }
}
