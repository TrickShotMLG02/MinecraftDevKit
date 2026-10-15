package com.trickshotmlg.devkit.commands;

import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.permissions.PermissionNamespace;
import com.trickshotmlg.devkit.text.TextRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandRegistryTest {
    private static final Argument<String> TARGET = Arguments.word("target");
    private static final Argument<Integer> PAGE = Arguments.integer("page", 1, 50);

    @Test
    void buildsImmutableNestedTreeAndFindsAliases() {
        CommandDefinition definition = CommandRegistry.builder()
                .root("friends", root -> root.aliases("friend", "f")
                        .description("Friend commands")
                        .subcommand("requests", requests -> requests
                                .subcommand("accept", accept -> accept
                                        .required(TARGET)
                                        .execute(context -> { }))))
                .build();

        assertEquals("friends", definition.findRoot("f").orElseThrow().name());
        assertEquals("requests", definition.roots().get(0).children().get(0).name());
        assertThrows(UnsupportedOperationException.class, () -> definition.roots().clear());
        CommandRegistry.Builder builder = CommandRegistry.builder();
        builder.root("x", root -> { });
        builder.build();
        assertThrows(IllegalStateException.class, () -> builder.root("later", root -> { }));
    }

    @Test
    void supportsTypedRequiredAndOptionalArgumentDeclarations() {
        CommandDefinition definition = CommandRegistry.builder().root("friends", root -> root
                .subcommand("list", list -> list.optional(PAGE, 1).execute(context -> { }))
                .subcommand("add", add -> add.required(TARGET).execute(context -> { }))).build();
        CommandNode list = definition.roots().get(0).children().get(0);
        CommandNode add = definition.roots().get(0).children().get(1);
        assertFalse(list.arguments().get(0).required());
        assertEquals(1, list.arguments().get(0).defaultValue().orElseThrow());
        assertSame(TARGET, add.arguments().get(0).argument());
        assertEquals(ArgumentType.INTEGER, PAGE.type());
    }

    @Test
    void typedContextRetrievalDoesNotUseStringKeys() {
        CommandContext context = new CommandContext(new FakeSender(), java.util.Map.of(TARGET, "alex", PAGE, 2));
        assertEquals("alex", context.get(TARGET));
        assertEquals(2, context.get(PAGE));
        assertEquals("alex", context.getOptional(TARGET).orElseThrow());
    }

    @Test
    void supportsAnOptionalValueWithoutUnexpectedNull() {
        java.util.Map<Argument<?>, Object> values = new java.util.HashMap<>();
        values.put(TARGET, "alex");
        values.put(PAGE, null);
        CommandContext context = new CommandContext(new FakeSender(), values);
        assertTrue(context.getOptional(PAGE).isEmpty());
        assertThrows(IllegalStateException.class, () -> context.get(PAGE));
    }

    @Test
    void supportsEnumAndConstrainedNumericArguments() {
        Argument<Integer> count = Arguments.integer("count", 1, 10);
        Argument<Mode> mode = Arguments.enumeration("mode", Mode.class);
        assertEquals(1, count.minimum().orElseThrow());
        assertEquals(10, count.maximum().orElseThrow());
        assertEquals(Mode.class, mode.enumType().orElseThrow());
        assertEquals("Page number", ((com.trickshotmlg.devkit.text.TextRef.Literal)
                PAGE.withDescription("Page number").description().orElseThrow()).value());
        assertThrows(IllegalArgumentException.class, () -> Arguments.integer("bad", 5, 1));
    }

    @Test
    void rejectsDuplicateRootNamesAndAliases() {
        assertThrows(IllegalArgumentException.class, () -> CommandRegistry.builder()
                .root("friends", root -> root.aliases("f"))
                .root("f", root -> { }).build());
        assertThrows(IllegalArgumentException.class, () -> CommandRegistry.builder()
                .root("friends", root -> root.aliases("f", "f")).build());
    }

    @Test
    void rejectsDuplicateSiblingLiteralsAndAliases() {
        assertThrows(IllegalArgumentException.class, () -> CommandRegistry.builder()
                .root("friends", root -> root
                        .subcommand("list", child -> child.aliases("l"))
                        .subcommand("remove", child -> child.aliases("l"))).build());
    }

    @Test
    void rejectsInvalidCommandAndArgumentNames() {
        assertThrows(IllegalArgumentException.class, () -> CommandRegistry.builder().root("Friends", root -> { }));
        assertThrows(IllegalArgumentException.class, () -> Arguments.word("target-name"));
        assertThrows(IllegalArgumentException.class, () -> CommandRegistry.builder().root("ok", root -> root.aliases("UP")));
    }

    @Test
    void rejectsRequiredArgumentAfterOptionalAndNonterminalGreedyArgument() {
        Argument<String> rest = Arguments.greedyString("rest");
        assertThrows(IllegalArgumentException.class, () -> CommandRegistry.builder().root("x", root -> root
                .optional(PAGE).required(TARGET)).build());
        assertThrows(IllegalArgumentException.class, () -> CommandRegistry.builder().root("x", root -> root
                .required(rest).required(TARGET)).build());
    }

    @Test
    void keepsLiteralBranchAlongsideArgumentBranchForCloudLiteralPrecedence() {
        CommandDefinition definition = CommandRegistry.builder().root("friends", root -> root
                .subcommand("accept", accept -> accept
                        .required(TARGET)
                        .subcommand("all", all -> all.execute(context -> { })))).build();
        CommandNode accept = definition.roots().get(0).children().get(0);
        assertEquals(ArgumentType.WORD, accept.arguments().get(0).argument().type());
        assertEquals("all", accept.children().get(0).name());
    }

    @Test
    void permissionAndSenderRequirementsAreInheritedAsAndConditions() {
        PermissionNamespace namespace = PermissionNamespace.create("test");
        Permission parent = namespace.permission("admin");
        Permission reload = namespace.permission("reload");
        namespace.build();
        CommandDefinition definition = CommandRegistry.builder().root("admin", admin -> admin
                .permission(parent).playerOnly()
                .subcommand("reload", child -> child.permission(reload).execute(context -> { }))).build();
        CommandNode reloadNode = definition.roots().get(0).children().get(0);
        assertEquals(List.of(parent, reload), reloadNode.permissionRequirements());
        assertEquals(List.of("player-only"), reloadNode.senderRequirements().stream().map(SenderRequirement::description).toList());
        assertFalse(reloadNode.isVisibleTo(new FakeSender()));
    }

    @Test
    void commandTextMetadataAcceptsLiteralAndTranslatedReferences() {
        CommandDefinition definition = CommandRegistry.builder().root("friends", root -> root
                .description(TextRef.translated("commands.friends.description", "Friends"))
                .usage("Usage: /friends")).build();
        assertEquals("commands.friends.description", definition.roots().get(0).description().orElseThrow()
                .translationKey().orElseThrow());
        assertEquals("Usage: /friends", ((TextRef.Literal) definition.roots().get(0).usageOverride().orElseThrow()).value());
    }

    @Test
    void failedSubcommandConfigurationDoesNotAppendPartialNode() {
        CommandRegistry.Builder registry = CommandRegistry.builder();
        assertThrows(IllegalArgumentException.class, () -> registry.root("friends", root -> root
                .subcommand("ok", child -> { throw new IllegalArgumentException("bad declaration"); })));
        assertEquals(0, registry.build().roots().size());
    }

    private enum Mode { FAST, SLOW }
    private static final class FakeSender implements CommandSender {
        @Override public SenderKind kind() { return SenderKind.OTHER; }
        @Override public java.util.Locale locale() { return java.util.Locale.ROOT; }
        @Override public boolean hasPermission(Permission permission) { return false; }
        @Override public void sendMessage(net.kyori.adventure.text.Component message) { }
    }
}
