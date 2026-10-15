package com.trickshotmlg.devkit.bukkit.commands;

import com.trickshotmlg.devkit.commands.Argument;
import com.trickshotmlg.devkit.commands.CommandDefinition;
import com.trickshotmlg.devkit.commands.CommandNode;
import com.trickshotmlg.devkit.commands.ArgumentBinding;
import com.trickshotmlg.devkit.commands.cloud.CloudCommandRegistrar;
import com.trickshotmlg.devkit.commands.error.CloudCommandErrorHandler;
import com.trickshotmlg.devkit.commands.error.CommandErrorMessages;
import com.trickshotmlg.devkit.commands.help.CommandHelpRenderer;
import com.trickshotmlg.devkit.text.TextProvider;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/** Registers MinecraftDevKit command definitions through Cloud's Bukkit/Paper integration. */
public final class BukkitCommandAdapter {
    private final LegacyPaperCommandManager<CommandSender> manager;
    private final Function<CommandSender, com.trickshotmlg.devkit.commands.CommandSender> senderAdapter;
    private final CloudCommandRegistrar<CommandSender> registrar;
    private final CloudCommandErrorHandler<CommandSender> errorHandler;
    private final java.util.Set<Argument<?>> playerParsers = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /**
     * Creates a synchronous legacy command manager suitable for Bukkit/Spigot and Paper plugins.
     * Create and register from {@code JavaPlugin#onEnable()} so Cloud can associate its native
     * registrations with the plugin lifecycle.
     * @param plugin owning plugin
     * @param textProvider per-sender text resolver
     * @param defaultLocale locale used for console and unknown player locale values
     */
    public BukkitCommandAdapter(JavaPlugin plugin, TextProvider textProvider, Locale defaultLocale) {
        this(plugin, textProvider, defaultLocale, CommandErrorMessages.defaults());
    }

    /**
     * Creates an adapter with replaceable localized command error messages.
     * @param plugin owning plugin
     * @param textProvider per-sender text resolver
     * @param defaultLocale locale used for console and unknown player locale values
     * @param errors localized framework error references
     */
    public BukkitCommandAdapter(JavaPlugin plugin, TextProvider textProvider, Locale defaultLocale,
                                CommandErrorMessages errors) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(textProvider, "textProvider");
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        if (defaultLocale.getLanguage().isEmpty()) throw new IllegalArgumentException("defaultLocale must have a language");
        Objects.requireNonNull(errors, "errors");
        try {
            manager = LegacyPaperCommandManager.createNative(plugin, ExecutionCoordinator.simpleCoordinator());
            if (manager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) manager.registerBrigadier();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not initialize Cloud's Bukkit command manager", exception);
        }
        BukkitCommandSenderAdapter bridge = new BukkitCommandSenderAdapter(defaultLocale);
        senderAdapter = bridge::adapt;
        registrar = new CloudCommandRegistrar<>(manager, senderAdapter, new CommandHelpRenderer(textProvider));
        errorHandler = new CloudCommandErrorHandler<>(manager, senderAdapter, textProvider, errors);
        errorHandler.install();
    }

    /**
     * Registers a validated immutable command tree.
     * @param definition command tree to register
     */
    public void register(CommandDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        registerPlayerParsers(definition.roots());
        registrar.register(definition);
    }

    /**
     * Returns Cloud's manager for advanced parser, preprocessor, and capability configuration.
     * @return backing Cloud manager
     */
    public LegacyPaperCommandManager<CommandSender> cloudManager() { return manager; }

    private void registerPlayerParsers(Iterable<CommandNode> nodes) {
        for (CommandNode node : nodes) {
            for (ArgumentBinding<?> binding : node.arguments()) {
                Argument<?> argument = binding.argument();
                if (argument.valueType() == Player.class && playerParsers.add(argument)) {
                    @SuppressWarnings("unchecked")
                    Argument<Player> playerArgument = (Argument<Player>) argument;
                    registrar.parser(playerArgument, PlayerParser.<CommandSender>playerParser());
                }
            }
            registerPlayerParsers(node.children());
        }
    }
}
