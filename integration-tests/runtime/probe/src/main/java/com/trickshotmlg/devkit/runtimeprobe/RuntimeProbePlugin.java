package com.trickshotmlg.devkit.runtimeprobe;

import com.trickshotmlg.devkit.bukkit.commands.BukkitCommandAdapter;
import com.trickshotmlg.devkit.bukkit.commands.BukkitCommandSender;
import com.trickshotmlg.devkit.bukkit.gui.BukkitGuiPlatformExtension;
import com.trickshotmlg.devkit.bukkit.gui.GuiButton;
import com.trickshotmlg.devkit.bukkit.gui.GuiManager;
import com.trickshotmlg.devkit.bukkit.gui.GuiMenu;
import com.trickshotmlg.devkit.bukkit.gui.ToggleButton;
import com.trickshotmlg.devkit.bukkit.gui.ConfirmGui;
import com.trickshotmlg.devkit.bukkit.gui.GuiPlatformCapabilities;
import com.trickshotmlg.devkit.bukkit.gui.ItemBuilder;
import com.trickshotmlg.devkit.bukkit.gui.PagedGui;
import com.trickshotmlg.devkit.bukkit.gui.RadioGroup;
import com.trickshotmlg.devkit.bukkit.lifecycle.BukkitLifecycle;
import com.trickshotmlg.devkit.bukkit.scheduler.BukkitTaskScheduler;
import com.trickshotmlg.devkit.commands.Arguments;
import com.trickshotmlg.devkit.commands.Argument;
import com.trickshotmlg.devkit.commands.CommandRegistry;
import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.permissions.PermissionNamespace;
import com.trickshotmlg.devkit.config.ConfigKey;
import com.trickshotmlg.devkit.config.ConfigSchema;
import com.trickshotmlg.devkit.config.yaml.YamlConfigHandle;
import com.trickshotmlg.devkit.config.yaml.YamlSaveOptions;
import com.trickshotmlg.devkit.lifecycle.LifecycleException;
import com.trickshotmlg.devkit.lifecycle.LifecycleManager;
import com.trickshotmlg.devkit.lifecycle.ServiceKey;
import com.trickshotmlg.devkit.localization.LocalizationService;
import com.trickshotmlg.devkit.localization.yaml.YamlTranslationSource;
import com.trickshotmlg.devkit.scheduler.AsyncTaskResult;
import com.trickshotmlg.devkit.scheduler.TaskHandle;
import com.trickshotmlg.devkit.scheduler.TaskScope;
import com.trickshotmlg.devkit.scheduler.TaskState;
import com.trickshotmlg.devkit.scheduler.TickDelay;
import com.trickshotmlg.devkit.scheduler.TickPeriod;
import com.trickshotmlg.devkit.storage.SqlParameters;
import com.trickshotmlg.devkit.storage.Storage;
import com.trickshotmlg.devkit.storage.migrations.StorageMigrations;
import com.trickshotmlg.devkit.text.StaticTextProvider;
import com.trickshotmlg.devkit.text.TextRef;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/** Test-only probe. It runs real MinecraftDevKit APIs inside the selected server process. */
public final class RuntimeProbePlugin extends JavaPlugin implements Listener, CommandExecutor {
    private static final ServiceKey<ProbeResource> RESOURCE = ServiceKey.of("runtime-resource", ProbeResource.class);
    private static final Argument<Integer> NESTED_VALUE = Arguments.integer("value", 1, 99);
    private static final ConfigKey<Integer> CONFIG_VALUE = ConfigKey.integer("probe.value", 7).range(0, 100).build();
    private static final ConfigSchema CONFIG_SCHEMA = ConfigSchema.builder().version(1).register(CONFIG_VALUE).build();
    private static final Permission INTERACTIVE_PERMISSION = PermissionNamespace.create("mdk_runtimeprobe")
            .permission("interactive.protected");

    private final CopyOnWriteArrayList<RuntimeCheck> checks = new CopyOnWriteArrayList<>();
    private GuiManager guiManager;
    private BukkitTaskScheduler scheduler;
    private TaskScope scope;
    private BukkitLifecycle bukkitLifecycle;
    private LifecycleManager lifecycle;
    private ProbeResource ownedResource;
    private YamlConfigHandle config;
    private LocalizationService localization;
    private Storage storage;
    private volatile boolean storageHandoffComplete;
    private InteractiveSessionState interactive;
    private PermissionAttachment interactivePermission;
    private boolean interactiveGuiOpen;
    private int cloudProtectedExecutions;
    private int nestedExecutions;
    private int confirmExecutions;
    private int cancelConfirmExecutions;
    private int toggleCallbackExecutions;

    @Override public void onEnable() {
        getDataFolder().mkdirs();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("devkitaccept") != null) getCommand("devkitaccept").setExecutor(this);
        check("dependent examples enabled", enabled("MinecraftDevKitIntegrationExample")
                        && enabled("MinecraftDevKitStorageExample"),
                "Both standalone examples are plugin.yml dependencies of this probe.");
        verifyCommonClassLoading();
        verifyGuiRuntime();
        verifyLifecycleRuntime();
        verifyConfigurationAndLocalization();
        verifyCommandRuntime();
        verifySchedulerRuntime();
        verifyStorageRuntime();
        writeReport();
    }

    private boolean enabled(String name) {
        Plugin plugin = getServer().getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }

    private void verifyCommonClassLoading() {
        try {
            Class.forName("com.trickshotmlg.devkit.bukkit.commands.BukkitCommandAdapter", false, getClassLoader());
            Class.forName("com.trickshotmlg.devkit.bukkit.gui.GuiManager", false, getClassLoader());
            Class.forName("com.trickshotmlg.devkit.bukkit.scheduler.BukkitTaskScheduler", false, getClassLoader());
            Class.forName("com.trickshotmlg.devkit.bukkit.lifecycle.BukkitLifecycle", false, getClassLoader());
            check("common adapter classloading", true, "Commands, GUI, scheduler, and lifecycle classes loaded from the probe's server classpath.");
        } catch (LinkageError | ClassNotFoundException failure) {
            check("common adapter classloading", false, failure.toString());
        }
    }

    private void verifyGuiRuntime() {
        try {
            boolean expectedPaper = "paper".equalsIgnoreCase(System.getProperty("mdk.runtime.platform", ""));
            boolean nativeComponents = GuiPlatformCapabilities.supportsNativeAdventureComponents();
            long providers = ServiceLoader.load(BukkitGuiPlatformExtension.class, getClassLoader()).stream().count();
            check("GUI extension discovery", nativeComponents == expectedPaper && providers == (expectedPaper ? 1 : 0),
                    "expectedPaper=" + expectedPaper + ", nativeComponents=" + nativeComponents + ", providers=" + providers);

            guiManager = GuiManager.create(this);
            ItemStack built = ItemBuilder.of(Material.DIAMOND)
                    .name(Component.text("Runtime Probe", net.kyori.adventure.text.format.NamedTextColor.GREEN))
                    .lore(List.of(Component.text("Unicode: 測試")))
                    .itemModel(new NamespacedKey(this, "runtime_probe"))
                    .build();
            check("common ItemBuilder metadata", built.getType() == Material.DIAMOND && built.hasItemMeta(),
                    "Name, lore, and common item-model metadata were applied by Bukkit ItemMeta.");

            GuiMenu menu = GuiMenu.chest(3).title(Component.text("Runtime Probe"))
                    .button(13, GuiButton.of(ItemBuilder.of(Material.EMERALD).name("Button").build(), context -> { }))
                    .build();
            check("GUI menu construction", menu.size() == 27, "Constructed a common chest menu and button.");

            InventoryHolder holder = () -> null;
            if (expectedPaper) {
                BukkitGuiPlatformExtension extension = ServiceLoader.load(BukkitGuiPlatformExtension.class, getClassLoader())
                        .findFirst().orElseThrow();
                Inventory inventory = extension.createInventory(holder, 9, Component.text("Native runtime title"));
                check("Paper native component inventory", inventory != null && inventory.getSize() == 9,
                        "Paper extension created an inventory using the Adventure title overload.");
            } else {
                Inventory inventory = Bukkit.createInventory(holder, 9,
                        LegacyComponentSerializer.legacySection().serialize(Component.text("Spigot runtime title")));
                check("Spigot string-title inventory", inventory != null && inventory.getSize() == 9,
                        "Spigot common inventory title path created a real server inventory.");
            }
        } catch (Throwable failure) {
            check("GUI runtime setup", false, failure.toString());
            getLogger().log(Level.SEVERE, "GUI runtime probe failed", failure);
        }
    }

    private void verifyLifecycleRuntime() {
        try {
            ownedResource = new ProbeResource();
            lifecycle = LifecycleManager.builder().service(RESOURCE, context -> ownedResource).build();
            bukkitLifecycle = BukkitLifecycle.create(this, lifecycle);
            bukkitLifecycle.start();
            check("Bukkit lifecycle start", lifecycle.state().name().equals("RUNNING")
                            && lifecycle.get(RESOURCE) == ownedResource && Bukkit.isPrimaryThread(),
                    "Typed service started on the primary server thread.");

            AtomicInteger rolledBack = new AtomicInteger();
            ServiceKey<ProbeResource> acquired = ServiceKey.of("rollback-resource", ProbeResource.class);
            ServiceKey<String> failureKey = ServiceKey.of("expected-failure", String.class);
            LifecycleManager failing = LifecycleManager.builder()
                    .service(acquired, context -> new ProbeResource(rolledBack))
                    .service(failureKey, context -> { throw new IllegalStateException("intentional runtime rollback probe"); }, acquired)
                    .build();
            try {
                failing.start();
                check("lifecycle startup rollback", false, "The intentional failure unexpectedly started.");
            } catch (LifecycleException expected) {
                failing.close();
                check("lifecycle startup rollback", rolledBack.get() == 1,
                        "The primary startup failure rolled back the acquired resource exactly once.");
            }
        } catch (Throwable failure) {
            check("Bukkit lifecycle start", false, failure.toString());
            getLogger().log(Level.SEVERE, "Lifecycle runtime probe failed", failure);
        }
    }

    private void verifyConfigurationAndLocalization() {
        try {
            config = YamlConfigHandle.createIfMissing(getDataFolder().toPath().resolve("probe.yml"), CONFIG_SCHEMA);
            int cycle = Integer.getInteger("mdk.runtime.cycle", 1);
            boolean expectedExistingValue = config.store().get(CONFIG_VALUE) == (cycle == 1 ? 7 : 19);
            config.store().set(CONFIG_VALUE, 19);
            boolean saved = config.save(YamlSaveOptions.safeRewrite()).success();
            boolean reloaded = config.reload().success() && config.store().get(CONFIG_VALUE) == 19;
            check("typed YAML configuration and restart persistence", expectedExistingValue && saved && reloaded && Files.exists(config.file()),
                    "Cycle " + cycle + " saw " + (cycle == 1 ? "generated default" : "persisted value") + ", then saved and reloaded 19.");

            localization = LocalizationService.builder()
                    .source(YamlTranslationSource.bundled("lang", "en", "de"))
                    .defaultFallbackLocale(Locale.ENGLISH).defaultFallbackEnabled(true).build();
            boolean loaded = localization.reload().successful();
            String resolved = LegacyComponentSerializer.legacySection().serialize(localization.resolve(
                    TextRef.translated("runtime.ready"), Locale.ENGLISH, Map.of("name", "Runtime")));
            check("YAML localization and placeholder", loaded && resolved.contains("Ready Runtime"), resolved);
        } catch (Throwable failure) {
            check("configuration and localization", false, failure.toString());
            getLogger().log(Level.SEVERE, "Configuration/localization runtime probe failed", failure);
        }
    }

    private void verifyStorageRuntime() {
        try {
            Path file = getDataFolder().toPath().resolve("runtime.sqlite");
            storage = Storage.builder().sqlite(file).build();
            var migration = StorageMigrations.builder(storage).classLoader(getClass().getClassLoader())
                    .locations("classpath:db/migration/runtime").migrate();
            if (!migration.currentVersion().filter("1"::equals).isPresent()) {
                throw new IllegalStateException("Required runtime schema migration V1 was not applied");
            }
            int cycle = Integer.getInteger("mdk.runtime.cycle", 1);
            String migrated = storage.queryOne("SELECT value FROM runtime_probe WHERE id = ?",
                    SqlParameters.of(1), row -> row.getString("value")).orElse("");
            check("SQLite JDBC and restart-persistent Flyway migration", migration.migrationsExecuted() == (cycle == 1 ? 1 : 0)
                            && migration.currentVersion().orElse("").equals("1")
                            && migrated.equals("migration-ready") && Files.exists(file),
                    "Cycle " + cycle + " ran " + migration.migrationsExecuted() + " migration(s); schema and row persisted.");

            storage.async().queryOne("SELECT value FROM runtime_probe WHERE id = ?", SqlParameters.of(1),
                    row -> row.getString("value")).result().whenComplete((value, error) -> {
                try {
                    scope.sync().run(() -> {
                        storageHandoffComplete = true;
                        check("async storage to server-thread handoff", error == null && Bukkit.isPrimaryThread()
                                        && value != null && value.orElse("").equals("migration-ready"),
                                error == null ? "SQLite result was handed back through the Bukkit scheduler." : error.toString());
                        writeReport();
                    });
                } catch (IllegalStateException closedScope) {
                    check("async storage to server-thread handoff", false, "TaskScope closed before continuation: " + closedScope);
                    writeReport();
                }
            });
        } catch (Throwable failure) {
            check("SQLite storage and migrations", false, failure.toString());
            getLogger().log(Level.SEVERE, "Storage runtime probe failed", failure);
        }
    }

    private void verifyCommandRuntime() {
        try {
            var definition = CommandRegistry.builder().root("mdkruntimeprobe", root -> root
                    .execute(context -> {
                        context.sender().sendMessage(Component.text(statusLine()));
                        Player player = playerSender(context.sender());
                        if (player != null) recordInteractive(player, "command.root.player", true,
                                "Actual Cloud root handler ran for player UUID " + player.getUniqueId());
                    })
                    .subcommand("status", status -> status.execute(context -> {
                        context.sender().sendMessage(Component.text(statusLine()));
                        Player player = playerSender(context.sender());
                        if (player != null) recordInteractive(player, "command.status.player", true,
                                "Actual Cloud status subcommand ran for player UUID " + player.getUniqueId());
                    }))
                    .subcommand("nested", nested -> nested.required(NESTED_VALUE)
                            .execute(context -> {
                                int value = context.get(NESTED_VALUE);
                                nestedExecutions++;
                                context.sender().sendMessage(Component.text("MDK_RUNTIME_NESTED_" + value));
                                Player player = playerSender(context.sender());
                                if (player != null) onPlayerNestedCommand(player, value);
                            }))
                    ).build();
            new BukkitCommandAdapter(this, new StaticTextProvider(), Locale.ENGLISH).register(definition);
            var protectedCommand = CommandRegistry.builder().root("mdkacceptcloud", root -> root
                    .subcommand("protected", protectedAction -> protectedAction.permission(INTERACTIVE_PERMISSION).execute(context -> {
                        Player player = playerSender(context.sender());
                        if (player != null) {
                            cloudProtectedExecutions++;
                            recordInteractive(player, "command.permission-allowed", player.hasPermission(INTERACTIVE_PERMISSION.node()),
                                    "Actual permission-protected Cloud handler executed for player UUID " + player.getUniqueId());
                            send(player, Component.text("Cloud protected handler executed."));
                            if (interactive != null && interactive.playerId().equals(player.getUniqueId())
                                    && interactive.stage() == InteractiveSessionState.Stage.PERMISSION_GRANTED) {
                                if (interactivePermission != null) interactivePermission.setPermission(INTERACTIVE_PERMISSION.node(), false);
                                player.recalculatePermissions();
                                interactive.observe(player.getUniqueId(), "permission.revoked", !player.hasPermission(INTERACTIVE_PERMISSION.node()),
                                        "Cloud grant was revoked after handler execution; hasPermission=" + player.hasPermission(INTERACTIVE_PERMISSION.node()),
                                        InteractiveSessionState.Stage.GUI_BUTTON);
                                send(player, Component.text("Grant revoked after protected execution; now run /devkitaccept gui."));
                                writeInteractiveReport(false);
                            }
                        }
                    }))).build();
            new BukkitCommandAdapter(this, new StaticTextProvider(), Locale.ENGLISH).register(protectedCommand);
            check("Cloud Bukkit command registration", true, "Registered root, nested argument, and status commands during onEnable.");
        } catch (Throwable failure) {
            check("Cloud Bukkit command registration", false, failure.toString());
            getLogger().log(Level.SEVERE, "Command runtime probe failed", failure);
        }
    }

    private static Player playerSender(Object sender) {
        return sender instanceof BukkitCommandSender bridge && bridge.bukkitSender() instanceof Player player ? player : null;
    }

    @EventHandler
    public void onInteractiveJoin(PlayerJoinEvent event) {
        send(event.getPlayer(), Component.text("Interactive MinecraftDevKit acceptance is available: run /devkitaccept help"));
    }

    @EventHandler
    public void onInteractivePlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!isInteractiveOwner(event.getPlayer()) || interactive == null) return;
        String commandLine = event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (commandLine.equals("/mdkacceptcloud protected")
                && interactive.stage() == InteractiveSessionState.Stage.PERMISSION_DENIED) {
            int executionsBefore = cloudProtectedExecutions;
            boolean deniedBeforeDispatch = !event.getPlayer().hasPermission(INTERACTIVE_PERMISSION.node());
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTask(this, () -> {
                if (interactive == null || !interactive.playerId().equals(player.getUniqueId())) return;
                boolean passed = deniedBeforeDispatch && cloudProtectedExecutions == executionsBefore
                        && interactive.stage() == InteractiveSessionState.Stage.PERMISSION_DENIED;
                if (interactive.record(player.getUniqueId(), InteractiveSessionState.Stage.PERMISSION_DENIED,
                        "command.permission-denied", passed,
                        "Real player command entered; permission denied before dispatch=" + deniedBeforeDispatch
                                + ", protected handler executions before/after=" + executionsBefore + "/" + cloudProtectedExecutions,
                        InteractiveSessionState.Stage.PERMISSION_GRANTED)) {
                    send(player, Component.text(passed ? "PASS: Cloud rejected the permission-protected command." : "FAIL: protected command was not denied."));
                    send(player, Component.text("Now run /devkitaccept grant, then retry /mdkacceptcloud protected."));
                    writeInteractiveReport(false);
                }
            });
        } else if (commandLine.equals("/mdkruntimeprobe nested nope")) {
            int executionsBefore = nestedExecutions;
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTask(this, () -> {
                boolean passed = nestedExecutions == executionsBefore;
                recordInteractive(player, "command.invalid-argument-rejected", passed,
                        "Real player supplied a non-integer argument; typed nested handler execution count stayed "
                                + executionsBefore + " -> " + nestedExecutions);
                send(player, Component.text(passed ? "PASS: invalid typed argument did not execute the handler." : "FAIL: invalid argument reached the handler."));
            });
        }
    }

    private static void send(Player player, Component message) {
        player.sendMessage(LegacyComponentSerializer.legacySection().serialize(message));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This acceptance command requires a real player client.");
            return true;
        }
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "help" -> sendInteractiveHelp(player);
            case "start" -> startInteractive(player);
            case "status" -> sendInteractiveStatus(player);
            case "retry" -> retryInteractive(player);
            case "grant" -> grantInteractivePermission(player);
            case "kit" -> giveInventoryProtectionItems(player);
            case "gui" -> openInteractiveGui(player);
            case "pages" -> openInteractivePages(player);
            case "cancelgui" -> openInteractiveCancelGui(player);
            case "finish" -> finishInteractive(player);
            case "cancel" -> cancelInteractive(player, "Player cancelled the interactive session.");
            default -> send(player, Component.text("Usage: /devkitaccept <help|start|status|retry|grant|kit|gui|pages|cancelgui|finish|cancel>"));
        }
        return true;
    }

    private void sendInteractiveHelp(Player player) {
        send(player, Component.text("MDK interactive acceptance: /devkitaccept start, then follow each prompt."));
        send(player, Component.text("Keep online-mode enabled. Use one authorized authenticated client. /devkitaccept cancel ends the session."));
        send(player, Component.text("GUI: test button, child/Back navigation, toggle twice, select Beta then Alpha radio, confirm, then /devkitaccept cancelgui and Cancel."));
        send(player, Component.text("Pagination: run /devkitaccept pages; click Next then Previous. Protection: while a test GUI is open, test shift-left/right, number-key, double-click, offhand swap, and a drag crossing a GUI slot."));
        send(player, Component.text("Run /devkitaccept kit before item-transfer checks. It adds only test items to free slots and an empty offhand; existing items are preserved."));
    }

    private void startInteractive(Player player) {
        if (interactive != null && interactive.stage() != InteractiveSessionState.Stage.COMPLETE
                && interactive.stage() != InteractiveSessionState.Stage.CANCELLED) {
            send(player, Component.text("An interactive session is already active: " + interactive.sessionId()));
            return;
        }
        clearInteractivePermission();
        interactive = new InteractiveSessionState(System.getProperty("mdk.runtime.platform", "unknown"), player.getUniqueId());
        interactivePermission = player.addAttachment(this);
        interactivePermission.setPermission(INTERACTIVE_PERMISSION.node(), false);
        player.recalculatePermissions();
        send(player, Component.text("Session " + interactive.sessionId() + " started for UUID " + player.getUniqueId()));
        send(player, Component.text("Step 1: run /mdkruntimeprobe, /mdkruntimeprobe status, /mdkruntimeprobe nested 42, and /mdkruntimeprobe nested nope."));
        writeInteractiveReport(false);
    }

    private void onPlayerNestedCommand(Player player, int value) {
        if (interactive == null || !interactive.playerId().equals(player.getUniqueId())) return;
        boolean passed = value == 42 && interactive.stage() == InteractiveSessionState.Stage.COMMAND;
        if (interactive.record(player.getUniqueId(), InteractiveSessionState.Stage.COMMAND, "command.nested.player",
                passed, "Cloud nested command executed for player UUID " + player.getUniqueId() + " with value=" + value,
                InteractiveSessionState.Stage.PERMISSION_DENIED)) {
            send(player, Component.text(passed ? "PASS: player command reached the typed nested handler." : "FAIL: nested command value or session stage was incorrect."));
            send(player, Component.text("Step 2: run /mdkacceptcloud protected while permission is explicitly denied."));
            writeInteractiveReport(false);
        }
    }

    private void grantInteractivePermission(Player player) {
        if (!ownsInteractive(player) || interactive.stage() != InteractiveSessionState.Stage.PERMISSION_GRANTED) {
            send(player, Component.text("Complete the denied permission check first."));
            return;
        }
        if (interactivePermission == null) interactivePermission = player.addAttachment(this);
        interactivePermission.setPermission(INTERACTIVE_PERMISSION.node(), true);
        player.recalculatePermissions();
        send(player, Component.text("Temporary session permission granted. Now run /mdkacceptcloud protected."));
    }

    private void giveInventoryProtectionItems(Player player) {
        if (!ownsInteractive(player)) return;
        player.getInventory().addItem(new ItemStack(Material.STONE), new ItemStack(Material.PAPER));
        if (player.getInventory().getItemInOffHand().getType().isAir())
            player.getInventory().setItemInOffHand(new ItemStack(Material.STICK));
        player.updateInventory();
        send(player, Component.text("Test items added to free inventory slots; existing items were not replaced. Use a Paper stack on the cursor for double-click collection tests."));
    }

    private void openInteractiveGui(Player player) {
        if (!ownsInteractive(player) || interactive.stage() != InteractiveSessionState.Stage.GUI_BUTTON) {
            send(player, Component.text("Complete the command and permission steps first."));
            return;
        }
        GuiMenu child = GuiMenu.chest(3).title("MDK acceptance navigation")
                .button(11, GuiButton.of(ItemBuilder.of(Material.ARROW).name("Back").build(), context -> {
                    var session = context.session();
                    context.back();
                    Bukkit.getScheduler().runTask(this, () -> {
                        boolean returned = !session.closed() && session.menu() != null;
                        recordInteractive(player, "gui.navigation", returned, "Client clicked child Back; GuiManager history depth=" + session.historyDepth());
                        send(player, Component.text("Navigation observed. Continue with toggle, confirm, cancel, and protection checks."));
                    });
                })).build();
        RadioGroup<String> radio = RadioGroup.<String>builder().key("acceptance-radio").selected("alpha")
                .option(19, "alpha", ItemBuilder.of(Material.LIME_DYE).name("Alpha selected").build(), ItemBuilder.of(Material.GRAY_DYE).name("Alpha").build())
                .option(20, "beta", ItemBuilder.of(Material.LIME_DYE).name("Beta selected").build(), ItemBuilder.of(Material.GRAY_DYE).name("Beta").build())
                .onChange((context, selected) -> {
                    String expectedName = "beta".equals(selected) ? "Beta selected" : "Alpha selected";
                    String actualName = displayName(player.getOpenInventory().getTopInventory().getItem(context.slot()));
                    recordInteractive(player, "gui.radio." + selected, expectedName.equals(actualName),
                            "Actual RadioGroup callback selected=" + selected + "; rendered selected item=" + actualName);
                    send(player, Component.text("Radio option selected: " + selected));
                }).build();
        GuiMenu root = GuiMenu.chest(3).title("MDK interactive acceptance")
                .button(9, GuiButton.of(ItemBuilder.of(Material.BOOK).name("Open pagination test").build(), context -> context.open(interactivePagedMenu())))
                .button(11, GuiButton.of(ItemBuilder.of(Material.COMPASS).name("Open child; click Back").build(), context -> context.open(child)))
                .button(13, GuiButton.of(ItemBuilder.of(Material.EMERALD).name("Button acceptance").build(), context -> {
                    recordInteractive(player, "gui.button", context.viewer().getUniqueId().equals(interactive.playerId()) && context.slot() == 13,
                            "Actual managed GuiButton callback received UUID=" + context.viewer().getUniqueId() + ", slot=" + context.slot() + ", click=" + context.clickType());
                    send(player, Component.text("PASS: real inventory click reached the managed button callback."));
                }))
                .component(15, ToggleButton.builder().key("acceptance-toggle").initialValue(false)
                        .onItem(ItemBuilder.of(Material.LIME_DYE).name("Toggle ON").build())
                        .offItem(ItemBuilder.of(Material.GRAY_DYE).name("Toggle OFF").build())
                        .onChange((context, enabled) -> {
                            int invocation = ++toggleCallbackExecutions;
                            String expectedName = enabled ? "Toggle ON" : "Toggle OFF";
                            String actualName = displayName(player.getOpenInventory().getTopInventory().getItem(context.slot()));
                            recordInteractive(player, invocation == 1 ? "gui.toggle.on" : "gui.toggle.off",
                                    enabled == (invocation == 1) && expectedName.equals(actualName),
                                    "Actual ToggleButton callback #" + invocation + " state=" + enabled + "; rendered item=" + actualName);
                            send(player, Component.text("Toggle state is now " + enabled));
                        }).build())
                .button(17, GuiButton.of(ItemBuilder.of(Material.PAPER).name("Open confirmation").build(), context ->
                        context.open(ConfirmGui.builder().title("Confirm acceptance action")
                                .onConfirm(click -> { confirmExecutions++; recordInteractive(player, "gui.confirm", true, "ConfirmGui callback was invoked by a client click; invocation=" + confirmExecutions); send(player, Component.text("Confirmation observed.")); })
                                .onCancel(click -> { click.back(); }).build())))
                .radioGroup(radio)
                .build();
        interactiveGuiOpen = true;
        var opened = guiManager.open(player, root);
        recordInteractive(player, "gui.session-owner", opened.viewerId().equals(player.getUniqueId())
                        && player.getOpenInventory().getTopInventory().getHolder() != null,
                "GuiManager returned a viewer-bound session and opened its held inventory.");
        send(player, Component.text("Real GUI opened. Click button, navigation, toggle, Beta radio, pagination book, and confirmation controls; then run /devkitaccept cancelgui and click Cancel."));
    }

    private GuiMenu interactivePagedMenu() {
        List<Integer> slots = java.util.stream.IntStream.range(0, 45).boxed().toList();
        return PagedGui.<Integer>chest(6).title("MDK pagination acceptance")
                .contentSlots(slots)
                .items(context -> java.util.stream.IntStream.rangeClosed(1, 90).boxed().toList())
                .render((context, value) -> GuiButton.of(ItemBuilder.of(Material.PAPER).name("Entry " + value).build(), click -> { }))
                .previousPage(45).pageIndicator(49).nextPage(53)
                .navigationItems(ItemBuilder.of(Material.ARROW).name("Previous").build(),
                        ItemBuilder.of(Material.ARROW).name("Next").build(), ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name("Disabled").build())
                .build();
    }

    private void openInteractivePages(Player player) {
        if (!ownsInteractive(player)) return;
        interactiveGuiOpen = true;
        guiManager.open(player, interactivePagedMenu());
        send(player, Component.text("Pagination opened. Click Next (bottom-right) and confirm Entry 46 begins the page; click Previous and confirm Entry 1 returns."));
    }

    private void openInteractiveCancelGui(Player player) {
        if (!ownsInteractive(player)) return;
        GuiMenu menu = ConfirmGui.builder().title("Cancel confirmation test")
                .onConfirm(context -> { cancelConfirmExecutions++; recordInteractive(player, "gui.cancel-dialog-not-confirmed", false, "Confirm was clicked instead of Cancel."); })
                .onCancel(context -> { recordInteractive(player, "gui.cancel", cancelConfirmExecutions == 0, "Cancel callback received a client click; cancellation-dialog confirm executions=" + cancelConfirmExecutions); send(player, Component.text("Cancellation observed; confirm action did not run.")); context.close(); })
                .build();
        interactiveGuiOpen = true;
        guiManager.open(player, menu);
        send(player, Component.text("Click the red Cancel item. Do not click Confirm for this cancellation check."));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInteractiveInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isInteractiveOwner(player) || !interactiveGuiOpen) return;
        int rawSlot = event.getRawSlot();
        if (rawSlot == 53 || rawSlot == 45) {
            boolean next = rawSlot == 53;
            Bukkit.getScheduler().runTask(this, () -> {
                String firstEntry = displayName(player.getOpenInventory().getTopInventory().getItem(0));
                String expected = next ? "Entry 46" : "Entry 1";
                boolean passed = expected.equals(firstEntry);
                recordInteractive(player, next ? "gui.pagination.next" : "gui.pagination.previous", passed,
                        "After a real page-control click, expected first item=" + expected + ", observed=" + firstEntry);
                send(player, Component.text(passed ? "PASS: " + (next ? "next" : "previous") + " page content verified." : "FAIL: page content did not match " + expected));
            });
        }
        boolean transferStyle = event.getClick().isShiftClick() || event.getClick().isKeyboardClick()
                || event.getClick().name().equals("DOUBLE_CLICK") || event.getAction().name().equals("COLLECT_TO_CURSOR");
        if (!transferStyle) return;
        String action = event.getClick().name().toLowerCase(Locale.ROOT);
        String clickName = event.getClick().name();
        boolean cancelled = event.isCancelled();
        ItemStack[] before = cloneContents(player.getInventory().getContents());
        ItemStack cursor = event.getCursor() == null ? null : event.getCursor().clone();
        Bukkit.getScheduler().runTask(this, () -> {
            boolean unchanged = java.util.Arrays.equals(before, player.getInventory().getContents())
                    && java.util.Objects.equals(cursor, player.getItemOnCursor());
            boolean passed = cancelled && unchanged;
            recordInteractive(player, "inventory.cancelled-click." + action, passed,
                    "Client-originated InventoryClickEvent click=" + clickName + ", rawSlot=" + rawSlot
                            + ", cancelled=" + cancelled + ", player inventory/cursor unchanged after event=" + unchanged);
            send(player, Component.text(passed ? "PASS: transfer-style click was cancelled and player inventory state stayed unchanged."
                    : "FAIL: transfer-style click was not cancelled or player inventory state changed."));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInteractiveInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isInteractiveOwner(player) || !interactiveGuiOpen) return;
        int topSize = event.getView().getTopInventory().getSize();
        boolean crossesGuiBoundary = event.getRawSlots().stream().anyMatch(slot -> slot < topSize)
                && event.getRawSlots().stream().anyMatch(slot -> slot >= topSize);
        if (!crossesGuiBoundary) return;
        String rawSlots = event.getRawSlots().toString();
        boolean cancelled = event.isCancelled();
        ItemStack[] before = cloneContents(player.getInventory().getContents());
        ItemStack cursor = event.getOldCursor() == null ? null : event.getOldCursor().clone();
        Bukkit.getScheduler().runTask(this, () -> {
            boolean unchanged = java.util.Arrays.equals(before, player.getInventory().getContents())
                    && java.util.Objects.equals(cursor, player.getItemOnCursor());
            boolean passed = cancelled && unchanged;
            recordInteractive(player, "inventory.protected-drag-cancelled", passed,
                    "Client-originated InventoryDragEvent rawSlots=" + rawSlots + ", crossed GUI boundary=true, cancelled=" + cancelled
                            + ", player inventory/cursor unchanged after event=" + unchanged);
            send(player, Component.text(passed ? "PASS: drag was cancelled and player inventory state stayed unchanged."
                    : "FAIL: cross-boundary drag was not cancelled or player inventory state changed."));
        });
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) copy[index] = contents[index] == null ? null : contents[index].clone();
        return copy;
    }

    private static String displayName(ItemStack item) {
        return item == null || !item.hasItemMeta() ? "" : item.getItemMeta().getDisplayName();
    }

    private void finishInteractive(Player player) {
        if (!ownsInteractive(player)) return;
        String[] required = {"command.root.player", "command.status.player", "command.nested.player", "command.invalid-argument-rejected",
                "command.permission-denied", "command.permission-allowed", "permission.revoked",
                "gui.session-owner", "gui.button", "gui.navigation", "gui.toggle.on", "gui.toggle.off", "gui.radio.beta", "gui.radio.alpha", "gui.confirm", "gui.cancel",
                "gui.pagination.next", "gui.pagination.previous",
                "inventory.cancelled-click.shift_left", "inventory.cancelled-click.shift_right",
                "inventory.cancelled-click.number_key", "inventory.cancelled-click.double_click",
                "inventory.cancelled-click.swap_offhand", "inventory.protected-drag-cancelled"};
        List<String> missing = java.util.Arrays.stream(required).filter(name -> !interactive.has(name)).toList();
        if (!missing.isEmpty()) {
            send(player, Component.text("Session is incomplete; missing: " + String.join(", ", missing)));
            writeInteractiveReport(false);
            return;
        }
        interactive.observe(player.getUniqueId(), "gui.confirm.once", confirmExecutions == 1,
                "ConfirmGui callback execution count=" + confirmExecutions, interactive.stage());
        interactive.observe(player.getUniqueId(), "gui.toggle.count", toggleCallbackExecutions == 2,
                "ToggleButton callback execution count=" + toggleCallbackExecutions + "; required exactly two actual clicks.", interactive.stage());
        if (cancelConfirmExecutions != 0) interactive.observe(player.getUniqueId(), "gui.cancel-confirm-not-run", false,
                "Cancellation menu confirm callback unexpectedly executed " + cancelConfirmExecutions + " time(s).", interactive.stage());
        else interactive.observe(player.getUniqueId(), "gui.cancel-confirm-not-run", true,
                "Cancellation menu confirm callback remained uncalled.", interactive.stage());
        if (interactive.assertions().stream().anyMatch(assertion -> "FAIL".equals(assertion.status()))) {
            send(player, Component.text("Session assertions contain a failure; see interactive-results.json."));
            writeInteractiveReport(false);
            return;
        }
        interactive.complete();
        clearInteractivePermission();
        writeInteractiveReport(true);
        send(player, Component.text("Interactive session completed. Review plugins/MdkRuntimeProbe/interactive-results.json before disconnecting."));
    }

    private void sendInteractiveStatus(Player player) {
        if (!ownsInteractive(player)) { send(player, Component.text("No active session owned by you.")); return; }
        send(player, Component.text("Session " + interactive.sessionId() + ", stage=" + interactive.stage() + ", assertions=" + interactive.assertions().size()));
    }

    private void retryInteractive(Player player) {
        if (!ownsInteractive(player)) return;
        List<String> failed = interactive.assertions().stream().filter(assertion -> "FAIL".equals(assertion.status()))
                .map(InteractiveSessionState.Assertion::scenario).toList();
        send(player, Component.text(failed.isEmpty() ? "No failed observations. Repeat the prompted action if a step is missing."
                : "Repeat the failed real action(s); a corrected observation replaces its failure: " + String.join(", ", failed)));
        sendInteractiveHelp(player);
    }

    private boolean ownsInteractive(Player player) {
        if (isInteractiveOwner(player)) return true;
        send(player, Component.text("Run /devkitaccept start first; the session is bound to one player UUID."));
        return false;
    }

    private boolean isInteractiveOwner(Player player) {
        return interactive != null && interactive.playerId().equals(player.getUniqueId());
    }

    private void cancelInteractive(Player player, String reason) {
        if (!ownsInteractive(player)) return;
        interactive.cancel(reason);
        clearInteractivePermission();
        if (guiManager != null && player.getOpenInventory().getTopInventory().getHolder() != null) player.closeInventory();
        interactiveGuiOpen = false;
        writeInteractiveReport(false);
        send(player, Component.text(reason));
    }

    private void recordInteractive(Player player, String name, boolean passed, String detail) {
        if (interactive == null || !interactive.playerId().equals(player.getUniqueId())) return;
        if (interactive.observe(player.getUniqueId(), name, passed, detail, interactive.stage())) {
            getLogger().info("[MDK-INTERACTIVE] " + (passed ? "PASS " : "FAIL ") + name + " session=" + interactive.sessionId());
            writeInteractiveReport(false);
        }
    }

    private void writeInteractiveReport(boolean complete) {
        if (interactive == null) return;
        String assertions = String.join(",", interactive.assertions().stream().map(assertion ->
                "{\"scenario\":\"" + escape(assertion.scenario()) + "\",\"status\":\"" + assertion.status()
                        + "\",\"detail\":\"" + escape(assertion.detail()) + "\",\"timestamp\":\"" + assertion.timestamp() + "\"}").toList());
        String json = "{\"schemaVersion\":1,\"sessionId\":\"" + interactive.sessionId() + "\",\"platform\":\""
                + escape(interactive.platform()) + "\",\"playerUuid\":\"" + interactive.playerId() + "\",\"complete\":" + complete
                + ",\"stage\":\"" + interactive.stage() + "\",\"assertions\":[" + assertions + "]}";
        try { Files.writeString(getDataFolder().toPath().resolve("interactive-results.json"), json); }
        catch (IOException failure) { getLogger().log(Level.SEVERE, "Could not write interactive acceptance report", failure); }
    }

    private void clearInteractivePermission() {
        if (interactivePermission == null) return;
        try { interactivePermission.remove(); } catch (IllegalArgumentException ignored) { }
        Player owner = interactive == null ? null : Bukkit.getPlayer(interactive.playerId());
        if (owner != null) owner.recalculatePermissions();
        interactivePermission = null;
    }

    @EventHandler
    public void onInteractiveQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (interactive != null && interactive.playerId().equals(event.getPlayer().getUniqueId())
                && interactive.stage() != InteractiveSessionState.Stage.COMPLETE) {
            interactive.cancel("Bound player disconnected before completing the interactive session.");
            clearInteractivePermission();
            interactiveGuiOpen = false;
            writeInteractiveReport(false);
        }
    }

    private void verifySchedulerRuntime() {
        try {
            scheduler = BukkitTaskScheduler.create(this);
            scope = scheduler.scope();
            scope.sync().run(() -> check("sync task primary-thread ownership", Bukkit.isPrimaryThread(),
                    "Sync scheduler callback executed on Bukkit primary thread."));

            AsyncTaskResult<String> async = scope.async().submit(() -> Thread.currentThread().getName());
            async.thenSync(worker -> check("async-to-sync scheduler continuation",
                    !worker.equals(Thread.currentThread().getName()) && Bukkit.isPrimaryThread(),
                    "Worker thread=" + worker + "; continuation ran on the primary thread."))
                    .onFailure(error -> check("async-to-sync scheduler continuation", false, error.toString()));

            TaskHandle cancelled = scope.sync().later(TickDelay.of(200),
                    () -> check("cancelled delayed task suppression", false, "A cancelled task executed."));
            boolean accepted = cancelled.cancel();
            check("task cancellation", accepted && cancelled.state() == TaskState.CANCELLED,
                    "A queued delayed task was cancelled before its due tick.");

            AtomicInteger repeatCount = new AtomicInteger();
            TaskHandle[] repeating = new TaskHandle[1];
            repeating[0] = scope.sync().repeat(TickDelay.of(1), TickPeriod.of(1), context -> {
                if (repeatCount.incrementAndGet() >= 2) {
                    context.cancel();
                    check("repeating task lifecycle", true, "Repeating task ran and cancelled itself after two ticks.");
                    writeReport();
                }
            });
        } catch (Throwable failure) {
            check("Bukkit scheduler runtime", false, failure.toString());
            getLogger().log(Level.SEVERE, "Scheduler runtime probe failed", failure);
        }
    }

    private String statusLine() {
        long passed = checks.stream().filter(check -> check.status().equals("PASS")).count();
        long failed = checks.stream().filter(check -> check.status().equals("FAIL")).count();
        return "MDK_RUNTIME_PROBE checks pass=" + passed + " fail=" + failed + " total=" + checks.size();
    }

    private void check(String name, boolean passed, String detail) {
        RuntimeCheck check = new RuntimeCheck(name, passed ? "PASS" : "FAIL", detail);
        checks.add(check);
        if (passed) getLogger().info("[MDK-RUNTIME] PASS " + name + " — " + detail);
        else getLogger().severe("[MDK-RUNTIME] FAIL " + name + " — " + detail);
    }

    private void writeReport() {
        String json = "{\"platform\":\"" + escape(System.getProperty("mdk.runtime.platform", "unknown"))
                + "\",\"checks\":[" + String.join(",", checks.stream().map(RuntimeCheck::json).toList()) + "]}";
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve("runtime-results.json"), json);
        } catch (IOException failure) {
            getLogger().log(Level.SEVERE, "Could not write runtime probe report", failure);
        }
    }

    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }

    @Override public void onDisable() {
        if (interactive != null && interactive.stage() != InteractiveSessionState.Stage.COMPLETE) {
            interactive.cancel("Server/plugin shutdown ended the interactive session before completion.");
            writeInteractiveReport(false);
        }
        clearInteractivePermission();
        if (bukkitLifecycle != null) {
            try {
                bukkitLifecycle.close();
                check("lifecycle shutdown and owned resource cleanup", lifecycle.state().name().equals("STOPPED")
                                && ownedResource.closeCount.get() == 1,
                        "Lifecycle manager closed its resource once on plugin disable.");
            } catch (Throwable failure) { check("lifecycle shutdown", false, failure.toString()); }
        }
        if (scope != null) {
            try { scope.close(); check("TaskScope shutdown", true, "TaskScope closed during plugin disable."); }
            catch (Throwable failure) { check("TaskScope shutdown", false, failure.toString()); }
        }
        if (scheduler != null) {
            try { scheduler.close(); check("Bukkit scheduler shutdown", scheduler.isClosed(), "Scheduler closed during plugin disable."); }
            catch (Throwable failure) { check("Bukkit scheduler shutdown", false, failure.toString()); }
        }
        if (guiManager != null) {
            try { guiManager.close(); check("GUI manager shutdown", true, "GUI manager closed during plugin disable."); }
            catch (Throwable failure) { check("GUI manager shutdown", false, failure.toString()); }
        }
        if (storage != null) {
            try { storage.close(); check("SQLite storage shutdown", storage.isClosed(), "SQLite pool and workers closed during plugin disable."); }
            catch (Throwable failure) { check("SQLite storage shutdown", false, failure.toString()); }
        }
        if (config != null) config.close();
        if (localization != null) localization.close();
        if (!storageHandoffComplete) check("async storage result completion", false, "Storage future did not reach the server-thread continuation before disable.");
        writeReport();
        getLogger().info("[MDK-RUNTIME] " + statusLine());
    }

    private static final class ProbeResource implements AutoCloseable {
        private final AtomicInteger closeCount;
        private ProbeResource() { this(new AtomicInteger()); }
        private ProbeResource(AtomicInteger closeCount) { this.closeCount = closeCount; }
        @Override public void close() { closeCount.incrementAndGet(); }
    }
}
