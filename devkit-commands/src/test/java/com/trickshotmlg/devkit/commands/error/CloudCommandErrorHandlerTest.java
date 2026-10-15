package com.trickshotmlg.devkit.commands.error;

import com.trickshotmlg.devkit.commands.CommandSender;
import com.trickshotmlg.devkit.commands.SenderKind;
import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.text.MissingTranslationPolicy;
import com.trickshotmlg.devkit.text.StaticTextProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.exception.ArgumentParseException;
import org.incendo.cloud.exception.CommandExecutionException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.exception.NoSuchCommandException;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.permission.PermissionResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CloudCommandErrorHandlerTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void localizesCloudErrorsAndSupportsApplicationOverrides() {
        TestManager manager = new TestManager();
        TestSender sender = new TestSender();
        CommandErrorMessages messages = CommandErrorMessages.defaults()
                .with(CommandErrorCategory.NO_PERMISSION, com.trickshotmlg.devkit.text.TextRef.literal("Access denied"));
        new CloudCommandErrorHandler<>(manager, value -> value, new StaticTextProvider(MissingTranslationPolicy.SHOW_KEY),
                messages, CommandErrorReporter.NO_OP).install();

        assertDoesNotThrow(() -> manager.exceptionController().handleException(new CommandContext<>(sender, manager),
                new NoSuchCommandException(sender, List.of(), "friends typo")));
        assertEquals("Unknown command.", PLAIN.serialize(sender.sent.remove(0)));

        var denied = new NoPermissionException(PermissionResult.of(false, org.incendo.cloud.permission.Permission.of("x")),
                sender, List.of());
        assertDoesNotThrow(() -> manager.exceptionController().handleException(new CommandContext<>(sender, manager), denied));
        assertEquals("Access denied", PLAIN.serialize(sender.sent.remove(0)));

        var syntax = new InvalidSyntaxException("/friends", sender, List.of());
        assertDoesNotThrow(() -> manager.exceptionController().handleException(new CommandContext<>(sender, manager), syntax));
        assertEquals("Invalid command syntax.", PLAIN.serialize(sender.sent.remove(0)));

        var missing = new InvalidSyntaxException("/friends add <player>", sender, List.of());
        assertDoesNotThrow(() -> manager.exceptionController().handleException(new CommandContext<>(sender, manager), missing));
        assertEquals("A required argument is missing.", PLAIN.serialize(sender.sent.remove(0)));

        var invalidArgument = new ArgumentParseException(new IllegalArgumentException("secret parser details"), sender, List.of());
        assertDoesNotThrow(() -> manager.exceptionController().handleException(new CommandContext<>(sender, manager), invalidArgument));
        assertEquals("One of the arguments is invalid.", PLAIN.serialize(sender.sent.remove(0)));
    }

    @Test
    void reportsExecutionCauseWithoutSendingInternalDetails() {
        TestManager manager = new TestManager();
        TestSender sender = new TestSender();
        AtomicReference<Throwable> reported = new AtomicReference<>();
        new CloudCommandErrorHandler<>(manager, value -> value, new StaticTextProvider(MissingTranslationPolicy.SHOW_KEY),
                CommandErrorMessages.defaults(), (category, failure) -> {
                    assertEquals(CommandErrorCategory.EXECUTION_ERROR, category);
                    reported.set(failure);
                }).install();
        IllegalStateException internal = new IllegalStateException("database password=secret");
        var failure = new CommandExecutionException(internal);

        assertDoesNotThrow(() -> manager.exceptionController().handleException(new CommandContext<>(sender, manager), failure));
        assertSame(internal, reported.get());
        assertEquals("The command could not be completed.", PLAIN.serialize(sender.sent.get(0)));
        assertFalse(PLAIN.serialize(sender.sent.get(0)).contains("secret"));
    }

    @Test
    void defaultsContainAllDocumentedErrorCategories() {
        CommandErrorMessages defaults = CommandErrorMessages.defaults();
        for (CommandErrorCategory category : CommandErrorCategory.values()) {
            assertNotNull(defaults.message(category));
            assertTrue(defaults.message(category).translationKey().isPresent());
        }
    }

    private static final class TestSender implements CommandSender {
        private final Set<String> permissions = new HashSet<>();
        private final List<Component> sent = new ArrayList<>();
        @Override public SenderKind kind() { return SenderKind.PLAYER; }
        @Override public Locale locale() { return Locale.US; }
        @Override public boolean hasPermission(Permission permission) { return permissions.contains(permission.node()); }
        @Override public void sendMessage(Component message) { sent.add(message); }
    }

    private static final class TestManager extends CommandManager<TestSender> {
        private TestManager() {
            super(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler());
        }
        @Override public boolean hasPermission(TestSender sender, String permission) {
            return sender.permissions.contains(permission);
        }
    }
}
