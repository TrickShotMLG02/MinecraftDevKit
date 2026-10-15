package com.trickshotmlg.devkit.commands.error;

import com.trickshotmlg.devkit.commands.CommandSender;
import com.trickshotmlg.devkit.commands.cloud.CommandAccessPermission;
import com.trickshotmlg.devkit.text.TextProvider;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.exception.ArgumentParseException;
import org.incendo.cloud.exception.CommandExecutionException;
import org.incendo.cloud.exception.InvalidCommandSenderException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.exception.NoSuchCommandException;
import org.incendo.cloud.exception.handling.ExceptionContext;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Maps Cloud v2 failures to localized MinecraftDevKit messages through Cloud's exception controller. */
public final class CloudCommandErrorHandler<C> {
    private final CommandManager<C> manager;
    private final Function<C, CommandSender> senderAdapter;
    private final TextProvider textProvider;
    private final CommandErrorMessages messages;
    private final CommandErrorReporter reporter;
    private boolean installed;

    /** Creates a handler using the JDK system logger for unexpected execution failures. */
    public CloudCommandErrorHandler(CommandManager<C> manager, Function<C, CommandSender> senderAdapter,
                                    TextProvider textProvider, CommandErrorMessages messages) {
        this(manager, senderAdapter, textProvider, messages, CommandErrorReporter.DEFAULT);
    }

    public CloudCommandErrorHandler(CommandManager<C> manager, Function<C, CommandSender> senderAdapter,
                                    TextProvider textProvider, CommandErrorMessages messages,
                                    CommandErrorReporter reporter) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.senderAdapter = Objects.requireNonNull(senderAdapter, "senderAdapter");
        this.textProvider = Objects.requireNonNull(textProvider, "textProvider");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
    }

    /** Installs handlers for Cloud's parsing, authorization, sender, and execution failures. */
    public synchronized void install() {
        if (installed) throw new IllegalStateException("Command error handlers have already been installed");
        var controller = manager.exceptionController();
        controller.registerHandler(NoSuchCommandException.class,
                context -> handle(context, CommandErrorCategory.UNKNOWN_COMMAND));
        controller.registerHandler(InvalidSyntaxException.class,
                context -> handle(context, invalidSyntaxCategory(context.exception())));
        controller.registerHandler(ArgumentParseException.class,
                context -> handle(context, CommandErrorCategory.INVALID_ARGUMENT));
        controller.registerHandler(NoPermissionException.class, context -> {
            CommandErrorCategory category = CommandErrorCategory.NO_PERMISSION;
            if (context.exception().missingPermission() instanceof CommandAccessPermission<?> access) {
                category = access.failureCategory(sender(context.context().sender()));
            }
            handle(context, category);
        });
        controller.registerHandler(InvalidCommandSenderException.class, context -> {
            CommandErrorCategory category = requiredSenderCategory(context.exception());
            handle(context, category);
        });
        controller.registerHandler(CommandExecutionException.class, context -> {
            Throwable failure = context.exception().getCause() == null
                    ? context.exception() : context.exception().getCause();
            reporter.report(CommandErrorCategory.EXECUTION_ERROR, failure);
            handle(context, CommandErrorCategory.EXECUTION_ERROR);
        });
        installed = true;
    }

    private <E extends Throwable> void handle(ExceptionContext<C, E> context, CommandErrorCategory category) {
        CommandSender sender = sender(context.context().sender());
        Map<String, String> placeholders = placeholders(context.exception());
        Component message = textProvider.resolve(messages.message(category), sender.locale(), placeholders);
        sender.sendMessage(message);
    }

    private CommandSender sender(C cloudSender) {
        return Objects.requireNonNull(senderAdapter.apply(cloudSender), "senderAdapter result");
    }

    private static Map<String, String> placeholders(Throwable exception) {
        Map<String, String> values = new LinkedHashMap<>();
        String command = "";
        String argument = "";
        if (exception instanceof org.incendo.cloud.exception.CommandParseException parseException) {
            command = parseException.currentChain().stream().map(component -> component.name())
                    .collect(java.util.stream.Collectors.joining(" "));
            if (!parseException.currentChain().isEmpty()) {
                argument = parseException.currentChain().get(parseException.currentChain().size() - 1).name();
            }
        } else if (exception instanceof NoSuchCommandException missing) {
            command = missing.suppliedCommand();
        }
        values.put("command", command);
        values.put("argument", argument);
        values.put("input", "");
        values.put("usage", exception instanceof InvalidSyntaxException syntax ? syntax.correctSyntax() : "");
        return Map.copyOf(values);
    }

    private static CommandErrorCategory requiredSenderCategory(InvalidCommandSenderException exception) {
        boolean console = exception.requiredSenderTypes().stream()
                .anyMatch(type -> type.getTypeName().toLowerCase(Locale.ROOT).contains("console"));
        return console ? CommandErrorCategory.CONSOLE_ONLY : CommandErrorCategory.PLAYER_ONLY;
    }

    private static CommandErrorCategory invalidSyntaxCategory(InvalidSyntaxException exception) {
        String syntax = exception.correctSyntax();
        for (int open = syntax.indexOf('<'); open >= 0; open = syntax.indexOf('<', open + 1)) {
            int close = syntax.indexOf('>', open + 1);
            if (close < 0) break;
            String argumentName = syntax.substring(open + 1, close);
            if (argumentName.contains("|")) continue;
            boolean alreadyParsed = exception.currentChain().stream().anyMatch(component ->
                    component.name().equals(argumentName));
            if (!alreadyParsed) return CommandErrorCategory.MISSING_ARGUMENT;
        }
        return CommandErrorCategory.INVALID_SYNTAX;
    }
}
