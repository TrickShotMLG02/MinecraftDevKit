package com.trickshotmlg.devkit.bukkit.gui;

import org.bukkit.event.Event;

import java.util.Objects;
import java.util.function.Predicate;

record GuiRefreshBinding<E extends Event>(Class<E> eventType, Predicate<E> filter, RefreshScope scope) {
    GuiRefreshBinding {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(scope, "scope");
    }
    @SuppressWarnings("unchecked") boolean matches(Event event) { return eventType.isInstance(event) && filter.test((E) event); }
}
