package com.trickshotmlg.devkit.examples.gui;

import com.trickshotmlg.devkit.bukkit.commands.BukkitCommandAdapter;
import com.trickshotmlg.devkit.bukkit.gui.ConfirmGui;
import com.trickshotmlg.devkit.bukkit.gui.GuiButton;
import com.trickshotmlg.devkit.bukkit.gui.GuiButtons;
import com.trickshotmlg.devkit.bukkit.gui.GuiItemTemplate;
import com.trickshotmlg.devkit.bukkit.gui.GuiItems;
import com.trickshotmlg.devkit.bukkit.gui.GuiManager;
import com.trickshotmlg.devkit.bukkit.gui.GuiMenu;
import com.trickshotmlg.devkit.bukkit.gui.GuiPatternContext;
import com.trickshotmlg.devkit.bukkit.gui.ItemBuilder;
import com.trickshotmlg.devkit.bukkit.gui.PagedGui;
import com.trickshotmlg.devkit.bukkit.gui.RadioGroup;
import com.trickshotmlg.devkit.bukkit.gui.RefreshScope;
import com.trickshotmlg.devkit.bukkit.gui.SkullTextureService;
import com.trickshotmlg.devkit.bukkit.gui.Slots;
import com.trickshotmlg.devkit.bukkit.gui.ToggleButton;
import com.trickshotmlg.devkit.commands.CommandDefinition;
import com.trickshotmlg.devkit.commands.CommandRegistry;
import com.trickshotmlg.devkit.localization.LocalizationService;
import com.trickshotmlg.devkit.localization.ReloadResult;
import com.trickshotmlg.devkit.localization.yaml.YamlTranslationSource;
import com.trickshotmlg.devkit.text.MissingTranslationPolicy;
import com.trickshotmlg.devkit.text.TextRef;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Small external-consumer plugin showcasing MinecraftDevKit's public GUI API. */
public final class GuiExamplePlugin extends JavaPlugin {
    private LocalizationService localization;
    private GuiManager guiManager;
    private SkullTextureService skulls;
    private GuiMenu mainMenu, playerList, settingsMenu, skullMenu, detailsMenu;

    @Override public void onEnable() {
        try {
            Files.createDirectories(getDataFolder().toPath().resolve("lang"));
            saveResource("lang/en.yml", false);
            saveResource("lang/de.yml", false);
            localization = LocalizationService.builder()
                    .source(YamlTranslationSource.bundled("lang", "en", "de"))
                    .source(YamlTranslationSource.directory(getDataFolder().toPath().resolve("lang")))
                    .defaultFallbackLocale(Locale.ENGLISH).defaultFallbackEnabled(true)
                    .missingKeyPolicy(MissingTranslationPolicy.SHOW_MARKER)
                    .diagnosticSink(diagnostic -> getLogger().warning(diagnostic.toString()))
                    .build();
            ReloadResult load = localization.reload();
            if (!load.successful()) throw new IllegalStateException(load.error().orElse("Translation load failed"));
            guiManager = GuiManager.create(this, localization);
            skulls = new SkullTextureService(this);
            createMenus();
            new BukkitCommandAdapter(this, localization, Locale.ENGLISH).register(openCommand());
        } catch (IOException | RuntimeException failure) {
            getLogger().severe("GUI example initialization failed: " + failure.getMessage());
            if (guiManager != null) guiManager.close();
            if (skulls != null) skulls.close();
            if (localization != null) localization.close();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private CommandDefinition openCommand() {
        return CommandRegistry.builder().root("mdkgui", root -> root.permission(GuiPermissions.OPEN).playerOnly()
                .description("Open the MinecraftDevKit GUI showcase")
                .execute(context -> {
                    if (context.sender() instanceof com.trickshotmlg.devkit.bukkit.commands.BukkitCommandSender bukkit
                            && bukkit.bukkitSender() instanceof Player player) guiManager.open(player, mainMenu);
                })).build();
    }

    private void createMenus() {
        ItemStack border = GuiItems.of(Material.BLUE_STAINED_GLASS_PANE, " ");
        ItemStack fill = GuiItems.grayPane();
        mainMenu = GuiMenu.chest(3)
                .title(TextRef.translated("gui.main.title", "MinecraftDevKit GUI"))
                .background(context -> context.row() == 0 || context.row() == context.rows() - 1
                        || context.column() == 0 || context.column() == 8 ? border.clone() : fill.clone())
                .button(11, GuiButton.of(context -> GuiItems.playerHead(context.viewer()), click -> click.open(playerList)))
                .button(13, GuiButton.of(ItemBuilder.of(Material.COMPARATOR).name("Settings").build(), click -> click.open(settingsMenu)))
                .button(15, GuiButton.of(ItemBuilder.of(Material.PLAYER_HEAD).name("Skull showcase").build(), click -> click.open(skullMenu)))
                .button(22, GuiButton.of(ItemBuilder.of(Material.TNT).name("Confirmation example").build(), click -> click.open(confirmation())))
                .build();

        playerList = PagedGui.<Player>chest(6)
                .title(TextRef.translated("gui.players.title", "Online players"))
                .contentSlots(Slots.rectangle(1, 1, 4, 7))
                .items(context -> List.copyOf(Bukkit.getOnlinePlayers()))
                .render((context, target) -> GuiButton.of(ctx -> ItemBuilder.from(GuiItems.playerHead(target))
                        .name(target.getName()).lore("Click to view this player").build(), click -> click.open(details(target))))
                .previousPage(45).pageIndicator(49, (context, page) -> ItemBuilder.of(Material.PAPER)
                        .name(context.text(TextRef.translated("gui.players.page", "Page {current}/{total}"),
                                Map.of("current", page.currentPage(), "total", page.totalPages())))
                        .lore("Online players: " + page.totalItems(), "Visible on page: " + page.visibleItemCount()).build())
                .nextPage(53).emptyState(44, GuiItems.of(Material.BARRIER, "No players online"))
                .refreshOn(PlayerJoinEvent.class, event -> true, RefreshScope.CONTENT)
                .refreshOn(PlayerQuitEvent.class, event -> true, RefreshScope.CONTENT)
                .build();

        settingsMenu = settingsMenu();
        skullMenu = skullMenu();
    }

    private GuiMenu settingsMenu() {
        ToggleButton sounds = ToggleButton.builder().key("sound-effects").initialValue(true)
                .onItem(ctx -> ItemBuilder.of(Material.GREEN_STAINED_GLASS_PANE).name("Sounds: enabled").build())
                .offItem(ctx -> ItemBuilder.of(Material.RED_STAINED_GLASS_PANE).name("Sounds: disabled").build())
                .onChange((ctx, enabled) -> ctx.viewer().sendMessage(Component.text("Sounds are now " + (enabled ? "enabled" : "disabled"))))
                .build();
        ToggleButton particles = ToggleButton.builder().key("particles").initialValue(false)
                .onItem(GuiItems.greenPane("Particles: enabled")).offItem(GuiItems.redPane("Particles: disabled")).build();
        RadioGroup<String> difficulty = RadioGroup.<String>builder().key("difficulty").selected("normal")
                .option(20, "easy", ctx -> named("Easy", Material.LIME_DYE), ctx -> named("Easy", Material.GRAY_DYE), null, ctx -> true, null)
                .option(22, "normal", ctx -> named("Normal", Material.GOLD_INGOT), ctx -> named("Normal", Material.GRAY_DYE), null, ctx -> true, null)
                .option(24, "hard", ctx -> named("Hard", Material.REDSTONE), ctx -> named("Hard", Material.GRAY_DYE), null, ctx -> true, null)
                .onChange((context, value) -> context.viewer().sendMessage(Component.text("Selected difficulty: " + value)))
                .build();
        return GuiMenu.chest(5).title(TextRef.translated("gui.settings.title", "Settings"))
                .button(11, GuiButton.of(GuiItems.of(Material.ARROW, "Back"), click -> click.back()))
                .component(10, sounds).component(16, particles).radioGroup(difficulty).build();
    }

    private GuiMenu skullMenu() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String hash = "0123456789abcdef0123456789abcdef";
        String base64 = Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/" + hash + "\"}}}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        GuiItemTemplate offline = GuiItems.offlinePlayerHead(id, skulls, guiManager,
                GuiItems.of(Material.SKELETON_SKULL, "Loading player skin…"));
        return GuiMenu.chest(3).title(TextRef.translated("gui.skulls.title", "Skull showcase"))
                .button(10, GuiButton.of(ctx -> GuiItems.playerHead(ctx.viewer()), click -> click.viewer().sendMessage(Component.text("Online profile"))))
                .button(12, GuiButton.of(offline, click -> { }))
                .button(14, GuiButton.of(GuiItems.skullTexture(hash), click -> click.viewer().sendMessage(Component.text("Texture hash"))))
                .button(16, GuiButton.of(GuiItems.skullBase64(base64), click -> click.viewer().sendMessage(Component.text("Base64 texture"))))
                .button(22, GuiButtons.back()).build();
    }

    private GuiMenu details(Player player) {
        return GuiMenu.chest(3).title("Player: " + player.getName())
                .item(13, GuiItems.playerHead(player))
                .button(22, GuiButtons.back()).build();
    }

    private GuiMenu confirmation() {
        return ConfirmGui.builder().title("Confirm action?")
                .onConfirm(context -> context.viewer().sendMessage(Component.text("Confirmed once.")))
                .onCancel(context -> { context.viewer().sendMessage(Component.text("Cancelled.")); context.back(); })
                .build();
    }

    private static ItemStack named(String name, Material material) { return ItemBuilder.of(material).name(name).build(); }

    @Override public void onDisable() {
        if (guiManager != null) guiManager.close();
        if (skulls != null) skulls.close();
        if (localization != null) localization.close();
    }
}
