package com.trickshotmlg.devkit.bukkit.gui;

import com.trickshotmlg.devkit.permissions.Permission;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Mutually exclusive typed choices rendered into several slots of one menu. */
public final class RadioGroup<T> {
    private final GuiStateKey<Object> stateKey;
    private final T initial;
    private final boolean allowNone;
    private final Function<GuiRenderContext, T> getter;
    private final BiConsumer<GuiRenderContext, T> setter;
    private final BiConsumer<GuiClickContext, T> onChange;
    private final Map<Integer, Option<T>> options;
    private RadioGroup(Builder<T> b) {
        stateKey = GuiStateKey.of(b.key, b.initial == null ? EmptySelection.INSTANCE : b.initial);
        initial = b.initial; allowNone = b.allowNone; getter = b.getter; setter = b.setter; onChange = b.onChange;
        options = Collections.unmodifiableMap(new LinkedHashMap<>(b.options));
    }
    public static <T> Builder<T> builder() { return new Builder<>(); }
    public Map<Integer, GuiComponent> components() {
        Map<Integer, GuiComponent> result = new LinkedHashMap<>();
        options.forEach((slot, option) -> result.put(slot, new OptionComponent<>(this, option)));
        return Collections.unmodifiableMap(result);
    }
    private T selected(GuiRenderContext context) {
        if (getter != null) {
            T selected = getter.apply(context);
            if (selected == null && !allowNone) throw new IllegalStateException("Required radio group binding returned no selection");
            if (selected != null && options.values().stream().noneMatch(option -> Objects.equals(option.value, selected)))
                throw new IllegalStateException("Radio group binding returned an unknown selection: " + selected);
            return selected;
        }
        Object value = context.session().get(stateKey);
        return value == EmptySelection.INSTANCE ? null : (T) value;
    }
    private void select(GuiClickContext context, T value) {
        T old = selected(new GuiRenderContext(context.viewer(), context.menu(), context.session(), context.slot()));
        T next = nextSelection(old, value, allowNone);
        if (Objects.equals(old, next)) return;
        GuiRenderContext render = new GuiRenderContext(context.viewer(), context.menu(), context.session(), context.slot());
        if (setter != null) {
            setter.accept(render, next);
            context.refresh();
        } else context.session().set(stateKey, next == null ? EmptySelection.INSTANCE : next);
        onChange.accept(context, next);
    }
    private enum EmptySelection { INSTANCE }
    static <T> T nextSelection(T current, T clicked, boolean allowNone) {
        return allowNone && Objects.equals(current, clicked) ? null : clicked;
    }
    private record Option<T>(T value, GuiItemTemplate selected, GuiItemTemplate unselected,
                             GuiItemTemplate disabled, Predicate<GuiRenderContext> enabled, Permission permission) { }
    private static final class OptionComponent<T> implements GuiComponent {
        private final RadioGroup<T> group; private final Option<T> option;
        private OptionComponent(RadioGroup<T> group, Option<T> option) { this.group = group; this.option = option; }
        @Override public boolean interactive() { return true; }
        @Override public boolean visible(GuiRenderContext context) { return option.permission == null || context.viewer().hasPermission(option.permission.node()); }
        @Override public ItemStack render(GuiRenderContext context) {
            if (!option.enabled.test(context)) return option.disabled == null ? null : option.disabled.render(context);
            return Objects.equals(group.selected(context), option.value) ? option.selected.render(context) : option.unselected.render(context);
        }
        @Override public void click(GuiClickContext context) {
            if (!option.enabled.test(new GuiRenderContext(context.viewer(), context.menu(), context.session(), context.slot(), this))
                    || option.permission != null && !context.viewer().hasPermission(option.permission.node())) return;
            group.select(context, option.value);
        }
    }
    public static final class Builder<T> {
        private String key = "radio";
        private T initial;
        private boolean allowNone;
        private Function<GuiRenderContext, T> getter;
        private BiConsumer<GuiRenderContext, T> setter;
        private BiConsumer<GuiClickContext, T> onChange = (context, value) -> { };
        private final Map<Integer, Option<T>> options = new LinkedHashMap<>();
        private final Set<T> values = new HashSet<>();
        public Builder<T> key(String key) { this.key = Objects.requireNonNull(key, "key"); return this; }
        public Builder<T> selected(T value) { initial = Objects.requireNonNull(value, "value"); return this; }
        public Builder<T> allowNone(boolean allowNone) { this.allowNone = allowNone; return this; }
        public Builder<T> option(int slot, T value, ItemStack selected, ItemStack unselected) {
            ItemStack selectedCopy = Objects.requireNonNull(selected, "selected").clone();
            ItemStack unselectedCopy = Objects.requireNonNull(unselected, "unselected").clone();
            return option(slot, value, context -> selectedCopy.clone(), context -> unselectedCopy.clone(), null, context -> true, null);
        }
        public Builder<T> option(int slot, T value, GuiItemTemplate selected, GuiItemTemplate unselected,
                                 GuiItemTemplate disabled, Predicate<GuiRenderContext> enabled, Permission permission) {
            if (slot < 0 || slot > 53) throw new IllegalArgumentException("Radio option slot must be between 0 and 53");
            Objects.requireNonNull(value, "value");
            if (values.contains(value)) throw new IllegalArgumentException("Duplicate radio option value: " + value);
            if (options.containsKey(slot)) throw new IllegalArgumentException("Duplicate radio option slot: " + slot);
            Option<T> option = new Option<>(value, Objects.requireNonNull(selected, "selected"),
                    Objects.requireNonNull(unselected, "unselected"), disabled, Objects.requireNonNull(enabled, "enabled"), permission);
            options.put(slot, option);
            values.add(value);
            return this;
        }
        public Builder<T> bind(Function<GuiRenderContext, T> getter, BiConsumer<GuiRenderContext, T> setter) {
            this.getter = Objects.requireNonNull(getter, "getter"); this.setter = Objects.requireNonNull(setter, "setter"); return this;
        }
        public Builder<T> onChange(BiConsumer<GuiClickContext, T> callback) { onChange = Objects.requireNonNull(callback, "onChange"); return this; }
        public RadioGroup<T> build() {
            if (options.isEmpty()) throw new IllegalStateException("Radio group needs at least one option");
            if ((getter == null) != (setter == null)) throw new IllegalStateException("External binding requires getter and setter");
            if (initial == null && !allowNone && getter == null) throw new IllegalStateException("Required radio group needs a selected value");
            if (initial != null && !values.contains(initial)) throw new IllegalStateException("Selected value is not an option: " + initial);
            return new RadioGroup<>(this);
        }
    }
}
