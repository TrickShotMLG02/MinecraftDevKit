package com.trickshotmlg.devkit.paper.commands;

import com.trickshotmlg.devkit.bukkit.commands.BukkitCommandSenderAdapter;
import com.trickshotmlg.devkit.commands.Argument;
import com.trickshotmlg.devkit.commands.ArgumentBinding;
import com.trickshotmlg.devkit.commands.CommandDefinition;
import com.trickshotmlg.devkit.commands.CommandNode;
import com.trickshotmlg.devkit.commands.cloud.CloudCommandRegistrar;
import com.trickshotmlg.devkit.commands.error.CloudCommandErrorHandler;
import com.trickshotmlg.devkit.commands.error.CommandErrorMessages;
import com.trickshotmlg.devkit.commands.help.CommandHelpRenderer;
import com.trickshotmlg.devkit.text.TextProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.bukkit.parser.PlayerParser;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Registers MinecraftDevKit's shared command trees through Cloud's native Paper lifecycle API. */
public final class PaperCommandAdapter implements AutoCloseable {
    private final PaperCommandManager<CommandSourceStack> manager;
    private final JavaPlugin plugin;
    private final Function<CommandSourceStack, com.trickshotmlg.devkit.commands.CommandSender> senderAdapter;
    private final CloudCommandRegistrar<CommandSourceStack> registrar;
    private final CloudCommandErrorHandler<CommandSourceStack> errorHandler;
    private final Set<Argument<?>> playerParsers = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final PaperCommandRootRegistry roots = new PaperCommandRootRegistry();
    private boolean closed;

    /**
     * Creates Cloud's native Paper manager from {@code JavaPlugin#onEnable()}. Paper owns the
     * resulting Brigadier lifecycle registrations and refreshes them when its command lifecycle
     * is rebuilt. Use this adapter instead of the Bukkit legacy adapter for the same command roots.
     * @param plugin owning JavaPlugin
     * @param textProvider per-sender text resolver
     * @param defaultLocale locale used for console and unknown player locale values
     */
    public PaperCommandAdapter(JavaPlugin plugin, TextProvider textProvider, Locale defaultLocale) {
        this(plugin, textProvider, defaultLocale, CommandErrorMessages.defaults());
    }

    /** Creates the native adapter with custom localized Cloud error messages.
     * @param plugin owning JavaPlugin
     * @param textProvider per-sender text resolver
     * @param defaultLocale locale used for console and unknown player locale values
     * @param errors localized command errors
     */
    public PaperCommandAdapter(JavaPlugin plugin, TextProvider textProvider, Locale defaultLocale,
                               CommandErrorMessages errors) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(textProvider, "textProvider");
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        if (defaultLocale.getLanguage().isEmpty()) throw new IllegalArgumentException("defaultLocale must have a language");
        Objects.requireNonNull(errors, "errors");
        this.plugin = plugin;
        try {
            manager = PaperCommandManager.builder()
                    .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                    .buildOnEnable(plugin);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not initialize Cloud's native Paper command manager", exception);
        }
        if (!manager.hasBrigadierManager()) {
            throw new IllegalStateException("Cloud's Paper manager did not initialize a native Brigadier registrar");
        }
        BukkitCommandSenderAdapter bridge = new BukkitCommandSenderAdapter(defaultLocale);
        senderAdapter = stack -> bridge.adapt(stack.getSender());
        registrar = new CloudCommandRegistrar<>(manager, senderAdapter, new CommandHelpRenderer(textProvider));
        errorHandler = new CloudCommandErrorHandler<>(manager, senderAdapter, textProvider, errors);
        errorHandler.install();
        plugin.getLogger().info("Initialized Cloud native Paper command registration");
    }

    /** Registers a validated immutable shared command definition through Paper's native command API.
     * @param definition command tree to register
     */
    public synchronized void register(CommandDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (closed) throw new IllegalStateException("Paper command adapter is closed");
        roots.register(definition, () -> {
            registerPlayerParsers(definition.roots());
            registrar.register(definition);
        });
    }

    /** The native Cloud manager for advanced Paper/Brigadier parser and suggestion configuration.
     * @return backing Paper manager
     */
    public PaperCommandManager<CommandSourceStack> cloudManager() { return manager; }

    /**
     * Stops further registrations. Paper removes lifecycle command registrations with the owning
     * plugin; this method does not unregister roots individually or alter Paper's command tree.
     */
    @Override public synchronized void close() { closed = true; }

    private void registerPlayerParsers(Iterable<CommandNode> nodes) {
        for (CommandNode node : nodes) {
            for (ArgumentBinding<?> binding : node.arguments()) {
                Argument<?> argument = binding.argument();
                if (argument.valueType() == Player.class && playerParsers.add(argument)) {
                    registerPlayerParser(argument);
                }
            }
            registerPlayerParsers(node.children());
        }
    }

    private <T> void registerPlayerParser(Argument<T> argument) {
        if (argument.type() != com.trickshotmlg.devkit.commands.ArgumentType.CUSTOM) return;
        registerTypedPlayerParser(argument);
    }

    @SuppressWarnings("unchecked")
    private <T> void registerTypedPlayerParser(Argument<T> argument) {
        registrar.parser((Argument<Player>) argument, PlayerParser.<CommandSourceStack>playerParser());
    }
}
