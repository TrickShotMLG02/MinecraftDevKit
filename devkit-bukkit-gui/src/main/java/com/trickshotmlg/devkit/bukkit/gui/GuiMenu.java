package com.trickshotmlg.devkit.bukkit.gui;

import net.kyori.adventure.text.Component;
import com.trickshotmlg.devkit.text.TextRef;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.Event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Immutable chest menu definition reusable by any number of players. */
public final class GuiMenu {
    private final int rows;
    private final Title title;
    private final Map<Integer, GuiComponent> components;
    private final GuiPattern background;
    private final List<GuiRefreshBinding<?>> refreshBindings;
    private final Set<Integer> contentRefreshSlots;
    private final GuiInteractionPolicy interactionPolicy;

    private GuiMenu(Builder builder) {
        rows = builder.rows;
        title = builder.title;
        components = Collections.unmodifiableMap(new LinkedHashMap<>(builder.components));
        background = builder.background;
        refreshBindings = List.copyOf(builder.refreshBindings);
        contentRefreshSlots = Collections.unmodifiableSet(new java.util.LinkedHashSet<>(builder.contentRefreshSlots));
        interactionPolicy = builder.interactionPolicy;
    }
    public static Builder chest(int rows) { return new Builder(rows); }
    public int rows() { return rows; }
    public int size() { return rows * 9; }
    public Map<Integer, GuiComponent> components() { return components; }
    GuiPattern background() { return background; }
    List<GuiRefreshBinding<?>> refreshBindings() { return refreshBindings; }
    Set<Integer> contentRefreshSlots() { return contentRefreshSlots; }
    GuiInteractionPolicy interactionPolicy() { return interactionPolicy; }
    Component title(GuiRenderContext context) {
        if (title.textRef != null && context.session().textProvider() != null)
            return context.session().textProvider().resolve(title.textRef, context.locale());
        if (title.textRef instanceof TextRef.Literal literal) return Component.text(literal.value());
        if (title.textRef != null) return Component.text(title.textRef.fallback()
                .orElseGet(() -> title.textRef.translationKey().orElse("")));
        return title.component;
    }

    private record Title(Component component, TextRef textRef) { }

    public static final class Builder {
        private final int rows;
        private Title title = new Title(Component.empty(), null);
        private final Map<Integer, GuiComponent> components = new LinkedHashMap<>();
        private GuiPattern background;
        private final List<GuiRefreshBinding<?>> refreshBindings = new java.util.ArrayList<>();
        private final Set<Integer> contentRefreshSlots = new java.util.LinkedHashSet<>();
        private GuiInteractionPolicy interactionPolicy = GuiInteractionPolicy.PROTECT_GUI;
        private Builder(int rows) {
            if (rows < 1 || rows > 6) throw new IllegalArgumentException("Chest rows must be between 1 and 6: " + rows);
            this.rows = rows;
        }
        public Builder title(String plainText) { title = new Title(Component.text(Objects.requireNonNull(plainText, "title")), null); return this; }
        public Builder title(Component component) { title = new Title(Objects.requireNonNull(component, "title"), null); return this; }
        public Builder title(TextRef reference) { title = new Title(null, Objects.requireNonNull(reference, "title")); return this; }
        public Builder item(int slot, ItemStack item) { return component(slot, new StaticItem(item)); }
        public Builder button(int slot, GuiButton button) { return component(slot, Objects.requireNonNull(button, "button")); }
        public Builder radioGroup(RadioGroup<?> group) {
            Map<Integer, GuiComponent> groupComponents = Objects.requireNonNull(group, "group").components();
            for (int slot : groupComponents.keySet()) {
                if (slot < 0 || slot >= rows * 9) throw new IllegalArgumentException("Slot " + slot + " is outside a " + rows + " row chest");
                if (components.containsKey(slot)) throw new IllegalArgumentException("Slot " + slot + " already has a component");
            }
            components.putAll(groupComponents);
            return this;
        }
        public Builder fillEmpty(ItemStack item) { background = Pattern.solid(Objects.requireNonNull(item, "item")); return this; }
        public Builder background(GuiPattern pattern) { background = Objects.requireNonNull(pattern, "pattern"); return this; }
        public Builder border(ItemStack item) { background = Pattern.border(Objects.requireNonNull(item, "item")); return this; }
        public Builder interactionPolicy(GuiInteractionPolicy policy) { interactionPolicy = Objects.requireNonNull(policy, "policy"); return this; }
        public <E extends Event> Builder refreshOn(Class<E> eventType, Predicate<E> filter, RefreshScope scope) {
            refreshBindings.add(new GuiRefreshBinding<>(eventType, filter, scope));
            return this;
        }
        public <E extends Event> Builder refreshOn(Class<E> eventType) {
            return refreshOn(eventType, ignored -> true, RefreshScope.FULL);
        }
        Builder refreshBinding(GuiRefreshBinding<?> binding) { refreshBindings.add(Objects.requireNonNull(binding, "binding")); return this; }
        Builder contentRefreshSlots(Iterable<Integer> slots) {
            for (Integer slot : slots) {
                if (slot == null || slot < 0 || slot >= rows * 9) throw new IllegalArgumentException("Invalid content refresh slot: " + slot);
                contentRefreshSlots.add(slot);
            }
            return this;
        }
        public Builder component(int slot, GuiComponent component) {
            if (slot < 0 || slot >= rows * 9) throw new IllegalArgumentException("Slot " + slot + " is outside a " + rows + " row chest");
            Objects.requireNonNull(component, "component");
            if (components.putIfAbsent(slot, component) != null) throw new IllegalArgumentException("Slot " + slot + " already has a component");
            return this;
        }
        public GuiMenu build() { return new GuiMenu(this); }
    }

    private static final class StaticItem implements GuiComponent {
        private final ItemStack item;
        private StaticItem(ItemStack item) { this.item = Objects.requireNonNull(item, "item").clone(); }
        @Override public ItemStack render(GuiRenderContext context) { return item.clone(); }
    }
}
