package com.trickshotmlg.devkit.bukkit.gui;

import com.trickshotmlg.devkit.permissions.Permission;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Per-session boolean control or an externally bound toggle. */
public final class ToggleButton implements GuiComponent {
    private final GuiStateKey<Boolean> key;
    private final GuiItemTemplate onItem, offItem, disabledItem;
    private final Function<GuiRenderContext, Boolean> getter;
    private final BiConsumer<GuiRenderContext, Boolean> setter;
    private final BiConsumer<GuiClickContext, Boolean> onChange;
    private final Predicate<GuiRenderContext> visible, enabled;
    private final Permission permission;
    private ToggleButton(Builder b) {
        key = GuiStateKey.of(b.key, b.initial);
        onItem = Objects.requireNonNull(b.onItem, "onItem");
        offItem = Objects.requireNonNull(b.offItem, "offItem");
        disabledItem = b.disabledItem;
        getter = b.getter;
        setter = b.setter;
        onChange = b.onChange;
        visible = b.visible;
        enabled = b.enabled;
        permission = b.permission;
    }
    public static Builder builder() { return new Builder(); }
    @Override public boolean interactive() { return true; }
    @Override public boolean visible(GuiRenderContext context) { return visible.test(context) && permitted(context.viewer()); }
    @Override public ItemStack render(GuiRenderContext context) {
        if (!enabled.test(context) || !permitted(context.viewer())) return disabledItem == null ? null : disabledItem.render(context);
        return (value(context) ? onItem : offItem).render(context);
    }
    @Override public void click(GuiClickContext context) {
        GuiRenderContext render = new GuiRenderContext(context.viewer(), context.menu(), context.session(), context.slot(), this);
        if (!enabled.test(render) || !permitted(context.viewer())) return;
        boolean next = nextValue(value(render));
        if (setter != null) setter.accept(render, next);
        else context.session().set(key, next);
        onChange.accept(context, next);
        context.refreshSlot(context.slot());
    }
    private boolean value(GuiRenderContext context) { return getter == null ? context.session().get(key) : Objects.requireNonNull(getter.apply(context), "toggle value"); }
    private boolean permitted(Player player) { return permission == null || player.hasPermission(permission.node()); }
    static boolean nextValue(boolean current) { return !current; }

    public static final class Builder {
        private String key = "toggle";
        private boolean initial;
        private GuiItemTemplate onItem, offItem, disabledItem;
        private Function<GuiRenderContext, Boolean> getter;
        private BiConsumer<GuiRenderContext, Boolean> setter;
        private BiConsumer<GuiClickContext, Boolean> onChange = (context, value) -> { };
        private Predicate<GuiRenderContext> visible = context -> true, enabled = context -> true;
        private Permission permission;
        public Builder key(String key) { this.key = Objects.requireNonNull(key, "key"); return this; }
        public Builder initialValue(boolean initial) { this.initial = initial; return this; }
        public Builder onItem(ItemStack item) { ItemStack copy = Objects.requireNonNull(item, "item").clone(); onItem = context -> copy.clone(); return this; }
        public Builder onItem(GuiItemTemplate item) { onItem = Objects.requireNonNull(item, "item"); return this; }
        public Builder offItem(ItemStack item) { ItemStack copy = Objects.requireNonNull(item, "item").clone(); offItem = context -> copy.clone(); return this; }
        public Builder offItem(GuiItemTemplate item) { offItem = Objects.requireNonNull(item, "item"); return this; }
        public Builder disabledItem(ItemStack item) { ItemStack copy = Objects.requireNonNull(item, "item").clone(); disabledItem = context -> copy.clone(); return this; }
        public Builder bind(Function<GuiRenderContext, Boolean> getter, BiConsumer<GuiRenderContext, Boolean> setter) {
            this.getter = Objects.requireNonNull(getter, "getter"); this.setter = Objects.requireNonNull(setter, "setter"); return this;
        }
        public Builder onChange(BiConsumer<GuiClickContext, Boolean> callback) { onChange = Objects.requireNonNull(callback, "onChange"); return this; }
        public Builder visibleWhen(Predicate<GuiRenderContext> value) { visible = Objects.requireNonNull(value, "visibleWhen"); return this; }
        public Builder enabledWhen(Predicate<GuiRenderContext> value) { enabled = Objects.requireNonNull(value, "enabledWhen"); return this; }
        public Builder permission(Permission permission) { this.permission = Objects.requireNonNull(permission, "permission"); return this; }
        public ToggleButton build() {
            if ((getter == null) != (setter == null)) throw new IllegalStateException("External toggle binding requires both getter and setter");
            return new ToggleButton(this);
        }
    }
}
