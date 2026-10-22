package com.trickshotmlg.devkit.bukkit.gui;

import com.trickshotmlg.devkit.permissions.Permission;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.Consumer;

/** Builds a small confirmation menu whose confirm action runs at most once per session. */
public final class ConfirmGui {
    private ConfirmGui() { }
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String title = "Confirm";
        private ItemStack confirm = GuiItems.greenPane("Confirm");
        private ItemStack cancel = GuiItems.redPane("Cancel");
        private Consumer<GuiClickContext> onConfirm = context -> { };
        private Consumer<GuiClickContext> onCancel = GuiClickContext::back;
        private Permission permission;
        public Builder title(String title) { this.title = Objects.requireNonNull(title, "title"); return this; }
        public Builder confirmItem(ItemStack item) { confirm = Objects.requireNonNull(item, "item").clone(); return this; }
        public Builder cancelItem(ItemStack item) { cancel = Objects.requireNonNull(item, "item").clone(); return this; }
        public Builder onConfirm(Consumer<GuiClickContext> callback) { onConfirm = Objects.requireNonNull(callback, "onConfirm"); return this; }
        public Builder onCancel(Consumer<GuiClickContext> callback) { onCancel = Objects.requireNonNull(callback, "onCancel"); return this; }
        public Builder permission(Permission permission) { this.permission = Objects.requireNonNull(permission, "permission"); return this; }
        public GuiMenu build() {
            GuiStateKey<Boolean> completed = GuiStateKey.of("confirm.completed", false);
            GuiButton.Builder confirmButton = GuiButton.builder().item(confirm).onClick(context -> {
                if (context.session().get(completed)) return;
                onConfirm.accept(context);
                context.session().set(completed, true);
                context.close();
            });
            if (permission != null) confirmButton.permission(permission);
            return GuiMenu.chest(3).title(title)
                    .item(13, GuiItems.of(Material.PAPER, "Are you sure?"))
                    .button(11, confirmButton.build())
                    .button(15, GuiButton.of(cancel, onCancel::accept))
                    .build();
        }
    }
}
