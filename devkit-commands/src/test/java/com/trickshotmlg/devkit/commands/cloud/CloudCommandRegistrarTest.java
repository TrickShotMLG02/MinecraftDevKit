package com.trickshotmlg.devkit.commands.cloud;

import com.trickshotmlg.devkit.commands.Argument;
import com.trickshotmlg.devkit.commands.Arguments;
import com.trickshotmlg.devkit.commands.CommandRegistry;
import com.trickshotmlg.devkit.commands.CommandSender;
import com.trickshotmlg.devkit.commands.SenderKind;
import com.trickshotmlg.devkit.permissions.Permission;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.parser.standard.UUIDParser;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CloudCommandRegistrarTest {
    private static final Argument<String> PLAYER = Arguments.word("player");
    private static final Argument<Integer> PAGE = Arguments.integer("page");

    @Test
    void registersNestedCommandsAndUsesCloudLiteralPrecedence() {
        AtomicReference<String> invoked = new AtomicReference<>();
        TestManager manager = new TestManager();
        var definition = CommandRegistry.builder().root("friends", root -> root
                .aliases("f")
                .subcommand("requests", requests -> requests
                        .subcommand("accept", accept -> accept
                                .required(PLAYER)
                                .execute(context -> invoked.set("player:" + context.get(PLAYER)))
                                .subcommand("all", all -> all.execute(context -> invoked.set("all")))))).build();

        new CloudCommandRegistrar<>(manager, Sender::asCommandSender).register(definition);
        manager.commandExecutor().executeCommand(new Sender(), "f requests accept all").join();

        assertEquals("all", invoked.get());
        assertTrue(manager.rootCommands().contains("friends"));
    }

    @Test
    void mapsTypedArgumentsAndOptionalDefaultsIntoTheFrameworkContext() {
        AtomicReference<String> received = new AtomicReference<>();
        TestManager manager = new TestManager();
        var definition = CommandRegistry.builder().root("friends", root -> root
                .subcommand("list", list -> list.optional(PAGE, 3).execute(context ->
                        received.set("page=" + context.get(PAGE))))).build();

        new CloudCommandRegistrar<>(manager, Sender::asCommandSender).register(definition);
        manager.commandExecutor().executeCommand(new Sender(), "friends list").join();
        assertEquals("page=3", received.get());

        manager.commandExecutor().executeCommand(new Sender(), "friends list 8").join();
        assertEquals("page=8", received.get());
    }

    @Test
    void enforcesTypedPermissionsThroughTheCloudPermissionBridge() {
        var permissions = com.trickshotmlg.devkit.permissions.PermissionNamespace.create("test");
        Permission view = permissions.permission("friends.view");
        permissions.build();
        TestManager manager = new TestManager();
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        var definition = CommandRegistry.builder().root("friends", root -> root
                .subcommand("list", list -> list.permission(view).execute(context -> called.set(true)))).build();
        new CloudCommandRegistrar<>(manager, Sender::asCommandSender).register(definition);

        var denied = manager.commandExecutor().executeCommand(new Sender(), "friends list");
        assertThrows(java.util.concurrent.CompletionException.class, denied::join);
        assertFalse(called.get());

        Sender allowed = new Sender();
        allowed.permissions.add(view.node());
        manager.commandExecutor().executeCommand(allowed, "friends list").join();
        assertTrue(called.get());
    }

    @Test
    void allowsARegisteredCustomCloudParser() {
        Argument<UUID> id = Arguments.custom("id", UUID.class);
        UUID expected = UUID.randomUUID();
        AtomicReference<UUID> received = new AtomicReference<>();
        TestManager manager = new TestManager();
        var definition = CommandRegistry.builder().root("lookup", root -> root
                .required(id).execute(context -> received.set(context.get(id)))).build();
        new CloudCommandRegistrar<>(manager, Sender::asCommandSender)
                .parser(id, UUIDParser.<Sender>uuidParser())
                .register(definition);

        manager.commandExecutor().executeCommand(new Sender(), "lookup " + expected).join();
        assertEquals(expected, received.get());
    }

    @Test
    void validatesCustomParsersBeforeRegisteringAnyCommands() {
        Argument<UUID> id = Arguments.custom("id", UUID.class);
        TestManager manager = new TestManager();
        var definition = CommandRegistry.builder().root("lookup", root -> root
                .required(id).execute(context -> { })).build();

        assertThrows(IllegalStateException.class,
                () -> new CloudCommandRegistrar<>(manager, Sender::asCommandSender).register(definition));
        assertTrue(manager.rootCommands().isEmpty());
    }

    @Test
    void delegatesArgumentSuggestionsToCloudWithTheCurrentInput() {
        Argument<String> player = Arguments.word("player").withSuggestions((context, input) ->
                java.util.concurrent.CompletableFuture.completedFuture(
                        java.util.List.of("alex", "alina").stream().filter(value -> value.startsWith(input)).toList()));
        TestManager manager = new TestManager();
        var definition = CommandRegistry.builder().root("friends", root -> root
                .subcommand("add", add -> add.required(player).execute(context -> { }))).build();
        new CloudCommandRegistrar<>(manager, Sender::asCommandSender).register(definition);

        var suggestions = manager.suggestionFactory().suggest(new Sender(), "friends add al").join();
        assertEquals(java.util.List.of("alex", "alina"),
                suggestions.list().stream().map(org.incendo.cloud.suggestion.Suggestion::suggestion).toList());
    }

    @Test
    void cloudDoesNotSuggestCommandsTheSenderCannotAccess() {
        var permissions = com.trickshotmlg.devkit.permissions.PermissionNamespace.create("test");
        Permission admin = permissions.permission("admin");
        permissions.build();
        TestManager manager = new TestManager();
        var definition = CommandRegistry.builder().root("friends", root -> root
                .subcommand("admin", command -> command.permission(admin).execute(context -> { }))).build();
        new CloudCommandRegistrar<>(manager, Sender::asCommandSender).register(definition);

        var suggestions = manager.suggestionFactory().suggest(new Sender(), "friends ").join();
        assertTrue(suggestions.list().stream().map(org.incendo.cloud.suggestion.Suggestion::suggestion)
                .noneMatch("admin"::equals));
    }

    private static final class Sender {
        private final Set<String> permissions = new HashSet<>();
        CommandSender asCommandSender() {
            return new CommandSender() {
                @Override public SenderKind kind() { return SenderKind.PLAYER; }
                @Override public Locale locale() { return Locale.US; }
                @Override public boolean hasPermission(Permission permission) { return permissions.contains(permission.node()); }
                @Override public void sendMessage(Component message) { }
            };
        }
    }

    private static final class TestManager extends CommandManager<Sender> {
        private TestManager() {
            super(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler());
        }
        @Override public boolean hasPermission(Sender sender, String permission) {
            return sender.permissions.contains(permission);
        }
    }
}
