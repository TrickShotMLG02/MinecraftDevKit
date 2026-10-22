package com.trickshotmlg.devkit.bukkit.gui;

import org.bukkit.entity.Player;
import com.trickshotmlg.devkit.text.TextRef;
import net.kyori.adventure.text.Component;

import java.util.Map;

import java.util.Locale;
import java.util.Objects;

/** Read-only player and menu information available while rendering a component. */
public record GuiRenderContext(Player viewer, GuiMenu menu, GuiSession session, int slot, GuiComponent component) {
    public GuiRenderContext {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(session, "session");
    }

    public GuiRenderContext(Player viewer, GuiMenu menu, GuiSession session) {
        this(viewer, menu, session, -1, null);
    }
    public GuiRenderContext(Player viewer, GuiMenu menu, GuiSession session, int slot) {
        this(viewer, menu, session, slot, null);
    }

    /** Returns the viewer's current client locale, falling back to English for malformed values. */
    @SuppressWarnings("deprecation")
    public Locale locale() {
        Locale locale = Locale.forLanguageTag(viewer.getLocale().replace('_', '-'));
        return locale.getLanguage().isEmpty() ? Locale.ENGLISH : locale;
    }

    /** Resolves a viewer-localized reference at render time, retaining TextRef fallback behavior. */
    public Component text(TextRef reference) { return text(reference, Map.of()); }
    public Component text(TextRef reference, Map<String, ?> placeholders) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(placeholders, "placeholders");
        if (session.textProvider() != null) return session.textProvider().resolve(reference, locale(), placeholders);
        if (reference instanceof TextRef.Literal literal) return Component.text(literal.value());
        return Component.text(reference.fallback().orElseGet(() -> reference.translationKey().orElse("")));
    }
}
