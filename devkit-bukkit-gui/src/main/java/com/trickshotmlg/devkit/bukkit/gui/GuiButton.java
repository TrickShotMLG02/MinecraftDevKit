package com.trickshotmlg.devkit.bukkit.gui;

import com.trickshotmlg.devkit.permissions.Permission;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Immutable interactive component with a dynamic item template and optional access rules. */
public final class GuiButton implements GuiComponent {
    private final GuiItemTemplate item;
    private final ClickHandler handler;
    private final Predicate<GuiRenderContext> visible;
    private final Predicate<GuiRenderContext> enabled;
    private final Permission permission;
    private final Set<ClickType> clickTypes;

    private GuiButton(Builder builder) {
        item = Objects.requireNonNull(builder.item, "item");
        handler = Objects.requireNonNull(builder.handler, "onClick");
        visible = builder.visible;
        enabled = builder.enabled;
        permission = builder.permission;
        clickTypes = Set.copyOf(builder.clickTypes);
    }

    public static Builder builder() { return new Builder(); }
    public static GuiButton of(ItemStack item, ClickHandler handler) {
        return builder().item(item).onClick(handler).build();
    }
    public static GuiButton of(GuiItemTemplate item, ClickHandler handler) {
        return builder().item(item).onClick(handler).build();
    }

    @Override public ItemStack render(GuiRenderContext context) { return item.render(context); }
    @Override public boolean visible(GuiRenderContext context) { return visible.test(context) && allowed(context.viewer()); }
    @Override public boolean interactive() { return true; }
    @Override public void click(GuiClickContext context) {
        if (allowed(context.viewer()) && enabled.test(new GuiRenderContext(context.viewer(), context.menu(), context.session()))
                && (clickTypes.isEmpty() || clickTypes.contains(context.clickType()))) handler.handle(context);
    }
    private boolean allowed(Player player) { return permission == null || player.hasPermission(permission.node()); }

    @FunctionalInterface public interface ClickHandler { void handle(GuiClickContext context); }

    public static final class Builder {
        private GuiItemTemplate item;
        private ClickHandler handler;
        private Predicate<GuiRenderContext> visible = ignored -> true;
        private Predicate<GuiRenderContext> enabled = ignored -> true;
        private Permission permission;
        private final EnumSet<ClickType> clickTypes = EnumSet.noneOf(ClickType.class);
        private Builder() { }
        public Builder item(ItemStack value) {
            ItemStack copy = Objects.requireNonNull(value, "item").clone();
            item = ignored -> copy.clone();
            return this;
        }
        public Builder item(GuiItemTemplate value) { item = Objects.requireNonNull(value, "item"); return this; }
        public Builder onClick(ClickHandler value) { handler = Objects.requireNonNull(value, "onClick"); return this; }
        public Builder visibleWhen(Predicate<GuiRenderContext> value) { visible = Objects.requireNonNull(value, "visibleWhen"); return this; }
        public Builder enabledWhen(Predicate<GuiRenderContext> value) { enabled = Objects.requireNonNull(value, "enabledWhen"); return this; }
        public Builder permission(Permission value) { permission = Objects.requireNonNull(value, "permission"); return this; }
        public Builder clickTypes(ClickType... values) {
            Objects.requireNonNull(values, "clickTypes");
            for (ClickType value : values) clickTypes.add(Objects.requireNonNull(value, "clickType"));
            return this;
        }
        public GuiButton build() { return new GuiButton(this); }
    }
}
