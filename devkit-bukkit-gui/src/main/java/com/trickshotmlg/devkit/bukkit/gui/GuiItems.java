package com.trickshotmlg.devkit.bukkit.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import java.util.UUID;
import com.trickshotmlg.devkit.text.TextRef;

import java.util.Objects;

/** Small factories for common decorative GUI items. */
public final class GuiItems {
    private GuiItems() { }
    public static ItemStack of(Material material, String plainName) {
        return ItemBuilder.of(material).name(Objects.requireNonNull(plainName, "plainName")).build();
    }
    public static ItemStack pane(Material material, Component name) {
        return ItemBuilder.of(material).name(Objects.requireNonNull(name, "name")).build();
    }
    public static ItemStack grayPane() { return of(Material.GRAY_STAINED_GLASS_PANE, " "); }
    public static ItemStack blackPane() { return of(Material.BLACK_STAINED_GLASS_PANE, " "); }
    public static ItemStack greenPane(String name) { return of(Material.GREEN_STAINED_GLASS_PANE, name); }
    public static ItemStack redPane(String name) { return of(Material.RED_STAINED_GLASS_PANE, name); }
    /** Builds an item template whose name is resolved for the current viewer each render. */
    public static GuiItemTemplate localized(Material material, TextRef name) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        return context -> ItemBuilder.of(material).name(context.text(name)).build();
    }
    public static ItemStack playerHead(Player player) { return SkullTextureService.onlineHead(player); }
    public static ItemStack skullTexture(String hashOrUrl) { return SkullTextureService.customHead(hashOrUrl); }
    public static ItemStack skullBase64(String base64) { return SkullTextureService.customHead(SkullTextureService.validateBase64Texture(base64).toExternalForm()); }

    /** Creates a reactive offline head template with a placeholder until the Bukkit profile API resolves it. */
    public static GuiItemTemplate offlinePlayerHead(UUID playerId, SkullTextureService service,
                                                     GuiManager manager, ItemStack placeholder) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(manager, "manager");
        ItemStack fallback = Objects.requireNonNull(placeholder, "placeholder").clone();
        return context -> {
            var cached = service.cached(playerId);
            if (cached.isPresent()) return SkullTextureService.head(cached.get());
            GuiComponent component = context.component();
            if (component != null && context.slot() >= 0) {
                service.resolve(playerId).whenComplete((profile, error) -> {
                    if (error != null) {
                        manager.reportAsyncFailure("Could not load player head " + playerId, error);
                        return;
                    }
                    manager.executeOnMainThread(() -> manager.refreshSlotIfCurrent(
                            context.session(), context.slot(), component));
                });
            }
            return fallback.clone();
        };
    }
}
