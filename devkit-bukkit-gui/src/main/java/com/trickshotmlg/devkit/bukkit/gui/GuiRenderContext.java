package com.trickshotmlg.devkit.bukkit.gui;

import org.bukkit.entity.Player;

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
}
