package com.trickshotmlg.devkit.commands.cloud;

import com.trickshotmlg.devkit.commands.CommandRegistry;
import com.trickshotmlg.devkit.commands.CommandSender;
import com.trickshotmlg.devkit.commands.SenderKind;
import com.trickshotmlg.devkit.commands.error.CloudCommandErrorHandler;
import com.trickshotmlg.devkit.commands.error.CommandErrorCategory;
import com.trickshotmlg.devkit.commands.error.CommandErrorMessages;
import com.trickshotmlg.devkit.commands.error.CommandErrorReporter;
import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.permissions.PermissionNamespace;
import com.trickshotmlg.devkit.text.MissingTranslationPolicy;
import com.trickshotmlg.devkit.text.StaticTextProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.permission.PermissionResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudAccessErrorClassificationTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void distinguishesPlayerAndConsoleRestrictionsFromPermissionFailures() throws Throwable {
        PermissionNamespace namespace = PermissionNamespace.create("test");
        Permission secure = namespace.permission("secure");
        namespace.build();
        var playerDefinition = CommandRegistry.builder().root("player", root -> root.playerOnly().execute(context -> { })).build();
        var consoleDefinition = CommandRegistry.builder().root("console", root -> root.consoleOnly().execute(context -> { })).build();
        var permissionDefinition = CommandRegistry.builder().root("secure", root -> root.permission(secure).execute(context -> { })).build();
        var playerAccess = new CommandAccessPermission<FakeSender>(playerDefinition.roots().get(0), value -> value.bridge());
        var consoleAccess = new CommandAccessPermission<FakeSender>(consoleDefinition.roots().get(0), value -> value.bridge());
        var permissionAccess = new CommandAccessPermission<FakeSender>(permissionDefinition.roots().get(0), value -> value.bridge());

        FakeManager manager = new FakeManager();
        FakeSender sender = new FakeSender();
        var messages = CommandErrorMessages.defaults();
        new CloudCommandErrorHandler<>(manager, value -> value.bridge(), new StaticTextProvider(MissingTranslationPolicy.SHOW_KEY),
                messages, CommandErrorReporter.NO_OP).install();

        manager.exceptionController().handleException(new CommandContext<>(sender, manager),
                new NoPermissionException(PermissionResult.of(false, playerAccess), sender, List.of()));
        assertEquals("This command can only be used by a player.", PLAIN.serialize(sender.sent.remove(0)));

        manager.exceptionController().handleException(new CommandContext<>(sender, manager),
                new NoPermissionException(PermissionResult.of(false, consoleAccess), sender, List.of()));
        assertEquals("This command can only be used from the console.", PLAIN.serialize(sender.sent.remove(0)));

        manager.exceptionController().handleException(new CommandContext<>(sender, manager),
                new NoPermissionException(PermissionResult.of(false, permissionAccess), sender, List.of()));
        assertEquals("You do not have permission to do that.", PLAIN.serialize(sender.sent.remove(0)));
    }

    private static final class FakeSender {
        private final List<Component> sent = new ArrayList<>();
        CommandSender bridge() {
            return new CommandSender() {
                @Override public SenderKind kind() { return SenderKind.OTHER; }
                @Override public Locale locale() { return Locale.US; }
                @Override public boolean hasPermission(Permission permission) { return false; }
                @Override public void sendMessage(Component message) { sent.add(message); }
            };
        }
    }

    private static final class FakeManager extends CommandManager<FakeSender> {
        FakeManager() {
            super(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler());
        }
        @Override public boolean hasPermission(FakeSender sender, String permission) { return false; }
    }
}
