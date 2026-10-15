package com.trickshotmlg.devkit.commands.help;

import com.trickshotmlg.devkit.commands.CommandRegistry;
import com.trickshotmlg.devkit.commands.CommandNode;
import com.trickshotmlg.devkit.commands.CommandSender;
import com.trickshotmlg.devkit.commands.SenderKind;
import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.permissions.PermissionNamespace;
import com.trickshotmlg.devkit.text.MissingTranslationPolicy;
import com.trickshotmlg.devkit.text.StaticTextProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CommandHelpRendererTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void generatesNestedAlternativesAndSeparateArgumentAndLiteralForms() {
        PermissionNamespace namespace = PermissionNamespace.create("help");
        Permission adminPermission = namespace.permission("admin");
        namespace.build();
        var definition = CommandRegistry.builder().root("friends", root -> root.aliases("f")
                .subcommand("list", list -> list.execute(context -> { }))
                .subcommand("requests", requests -> requests
                        .subcommand("accept", accept -> accept.required(com.trickshotmlg.devkit.commands.Arguments.word("player"))
                                .execute(context -> { }).subcommand("all", all -> all.execute(context -> { })))
                        .subcommand("deny", deny -> deny.execute(context -> { })))
                .subcommand("admin", admin -> admin.permission(adminPermission).execute(context -> { }))).build();
        var root = definition.roots().get(0);
        CommandUsageRenderer renderer = new CommandUsageRenderer(new StaticTextProvider(MissingTranslationPolicy.SHOW_KEY));
        TestSender normal = new TestSender();
        List<String> usages = renderer.usages(root, normal);

        assertTrue(usages.contains("/friends <list|requests>"));
        assertTrue(usages.contains("/f <list|requests>"));
        assertTrue(usages.contains("/friends requests <accept|deny>"));
        assertTrue(usages.contains("/friends requests accept <player>"));
        assertTrue(usages.contains("/friends requests accept all"));
        assertFalse(usages.stream().anyMatch(value -> value.contains("<admin>")));

        normal.permissions.add(adminPermission.node());
        assertTrue(renderer.usages(root, normal).contains("/friends <list|requests|admin>"));
    }

    @Test
    void respectsUsageOverridesAndResolvesThemAtRenderTime() {
        var definition = CommandRegistry.builder().root("friends", root -> root
                .usage(com.trickshotmlg.devkit.text.TextRef.translated("commands.friends.usage", "Use /friends"))
                .execute(context -> { }))
                .build();
        CommandUsageRenderer renderer = new CommandUsageRenderer(new StaticTextProvider(MissingTranslationPolicy.SHOW_KEY));
        assertEquals(List.of("Use /friends"), renderer.usages(definition.roots().get(0), new TestSender()));
    }

    @Test
    void createsLocalizedHelpWithVisibleChildrenAndPagination() {
        var definition = CommandRegistry.builder().root("friends", root -> root
                .description("Friend commands")
                .execute(context -> { })
                .subcommand("list", list -> list.description("List friends").execute(context -> { }))
                .subcommand("add", add -> add.aliases("invite").description("Add a friend").execute(context -> { }))
                .subcommand("remove", remove -> remove.description("Remove a friend").execute(context -> { }))).build();
        CommandNode root = definition.roots().get(0);
        TestSender sender = new TestSender();
        CommandHelpRenderer renderer = new CommandHelpRenderer(new StaticTextProvider(MissingTranslationPolicy.SHOW_KEY));

        List<Component> page = renderer.page(root, root, "/friends", sender, 2, 2);
        List<String> text = page.stream().map(PLAIN::serialize).toList();
        assertTrue(text.contains("Friend commands"));
        assertTrue(text.stream().anyMatch(line -> line.contains("Remove a friend")));
        assertTrue(text.contains("Page 2 of 2"));
        assertTrue(renderer.textReferences().entries().containsKey("commands.help.page"));
        assertThrows(IllegalArgumentException.class, () -> renderer.page(root, root, sender, 0, 2));
    }

    @Test
    void resolvesPaginationWithTheCurrentSenderLocaleAndNamedValues() {
        var definition = CommandRegistry.builder().root("friends", root -> root
                .execute(context -> { })
                .subcommand("one", child -> child.execute(context -> { }))
                .subcommand("two", child -> child.execute(context -> { }))
                .subcommand("three", child -> child.execute(context -> { }))).build();
        CommandNode root = definition.roots().get(0);
        var renderer = new CommandHelpRenderer((reference, locale, placeholders) -> Component.text(
                locale.getLanguage() + ":" + placeholders.get("page") + "/" + placeholders.get("pages")));

        List<String> lines = renderer.page(root, root, "/friends", new TestSender(Locale.GERMANY), 2, 2)
                .stream().map(PLAIN::serialize).toList();

        assertTrue(lines.contains("de:2/2"));
    }

    @Test
    void helpOmitsSubcommandsHiddenByPermissions() {
        PermissionNamespace namespace = PermissionNamespace.create("help");
        Permission staff = namespace.permission("staff");
        namespace.build();
        var definition = CommandRegistry.builder().root("friends", root -> root
                .subcommand("list", list -> list.execute(context -> { }))
                .subcommand("admin", admin -> admin.permission(staff).execute(context -> { }))).build();
        TestSender sender = new TestSender();
        List<String> lines = new CommandHelpRenderer(new StaticTextProvider(MissingTranslationPolicy.SHOW_KEY))
                .page(definition.roots().get(0), definition.roots().get(0), "/friends", sender, 1, 10)
                .stream().map(PLAIN::serialize).toList();
        assertTrue(lines.stream().anyMatch(line -> line.contains("list")));
        assertFalse(lines.stream().anyMatch(line -> line.contains("admin")));
    }

    @Test
    void directHelpForAnInaccessibleSelectedNodeDoesNotExposeItsDescription() {
        PermissionNamespace namespace = PermissionNamespace.create("help");
        Permission staff = namespace.permission("staff");
        namespace.build();
        var definition = CommandRegistry.builder().root("friends", root -> root
                .subcommand("admin", admin -> admin.permission(staff).description("Secret administration")
                        .execute(context -> { }))
        ).build();
        CommandNode root = definition.roots().get(0);
        CommandNode admin = root.children().get(0);

        List<String> lines = new CommandHelpRenderer(new StaticTextProvider(MissingTranslationPolicy.SHOW_KEY))
                .page(root, admin, "/friends admin", new TestSender(), 1, 10)
                .stream().map(PLAIN::serialize).toList();

        assertTrue(lines.isEmpty());
    }

    @Test
    void helpPagesOnlyRenderTheSelectedNodesImmediateSyntax() {
        var definition = CommandRegistry.builder().root("friends", root -> root
                .subcommand("requests", requests -> requests
                        .subcommand("accept", accept -> accept.execute(context -> { })))).build();
        CommandNode root = definition.roots().get(0);
        List<String> rootLines = new CommandHelpRenderer(new StaticTextProvider(MissingTranslationPolicy.SHOW_KEY))
                .page(root, root, "/friends", new TestSender(), 1, 10)
                .stream().map(PLAIN::serialize).toList();
        assertTrue(rootLines.contains("/friends <requests>"));
        assertFalse(rootLines.contains("/friends requests <accept>"));

        List<String> requestLines = new CommandHelpRenderer(new StaticTextProvider(MissingTranslationPolicy.SHOW_KEY))
                .page(root, root.children().get(0), "/friends requests", new TestSender(), 1, 10)
                .stream().map(PLAIN::serialize).toList();
        assertTrue(requestLines.contains("/friends requests <accept>"));
    }

    private static final class TestSender implements CommandSender {
        private final Locale locale;
        private final Set<String> permissions = new HashSet<>();
        private final List<Component> sent = new ArrayList<>();
        private TestSender() { this(Locale.US); }
        private TestSender(Locale locale) { this.locale = locale; }
        @Override public SenderKind kind() { return SenderKind.PLAYER; }
        @Override public Locale locale() { return locale; }
        @Override public boolean hasPermission(Permission permission) { return permissions.contains(permission.node()); }
        @Override public void sendMessage(Component message) { sent.add(message); }
    }
}
