package com.trickshotmlg.devkit.localization;

import net.kyori.adventure.text.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** A rendered message together with the locale/fallback path that produced it. */
public record TextResolution(Component component, Locale requestedLocale, Optional<Locale> resolvedLocale,
                             Fallback fallback, Optional<String> source) {
    /** Validates the rendered message and its provenance values. */
    public TextResolution {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(requestedLocale, "requestedLocale");
        Objects.requireNonNull(resolvedLocale, "resolvedLocale");
        Objects.requireNonNull(fallback, "fallback");
        Objects.requireNonNull(source, "source");
    }

    /** Kind of fallback used to obtain the displayed message. */
    public enum Fallback { NONE, PARENT_LOCALE, DEFAULT_LOCALE, TEXT_REF, MISSING_KEY_POLICY }
}
