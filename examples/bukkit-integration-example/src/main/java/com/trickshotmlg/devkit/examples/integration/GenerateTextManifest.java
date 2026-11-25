package com.trickshotmlg.devkit.examples.integration;

import com.trickshotmlg.devkit.commands.error.CommandErrorMessages;
import com.trickshotmlg.devkit.commands.help.CommandHelpRenderer;
import com.trickshotmlg.devkit.localization.TextCatalogManifest;
import com.trickshotmlg.devkit.text.MissingTranslationPolicy;
import com.trickshotmlg.devkit.text.StaticTextProvider;
import com.trickshotmlg.devkit.text.TextCatalog;
import com.trickshotmlg.devkit.text.TextRef;

import java.nio.file.Path;

/** Builds strict-scanner references for command and GUI text. */
public final class GenerateTextManifest {
    private GenerateTextManifest() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected generated manifest output path");
        TextCatalog app = TextCatalog.builder()
                .add(TextRef.translated("integration.menu.title", "MinecraftDevKit Integration"), "gui-title", "main")
                .add(TextRef.translated("integration.menu.profile"), "gui-item", "profile")
                .add(TextRef.translated("integration.menu.players", "Open the integration menus"), "gui-item", "players")
                .add(TextRef.translated("integration.menu.settings"), "gui-item", "settings")
                .add(TextRef.translated("integration.menu.status"), "gui-item", "status")
                .add(TextRef.translated("integration.menu.close"), "gui-item", "close")
                .add(TextRef.translated("integration.players.title"), "gui-title", "players")
                .add(TextRef.translated("integration.players.page"), "gui-item", "page")
                .add(TextRef.translated("integration.players.count"), "gui-lore", "count")
                .add(TextRef.translated("integration.players.empty"), "gui-empty", "players")
                .add(TextRef.translated("integration.players.detail"), "gui-item", "detail")
                .add(TextRef.translated("integration.players.detail-title"), "gui-title", "player-details")
                .add(TextRef.translated("integration.players.back"), "gui-item", "back")
                .add(TextRef.translated("integration.players.previous"), "gui-item", "previous")
                .add(TextRef.translated("integration.players.next"), "gui-item", "next")
                .add(TextRef.translated("integration.players.unavailable"), "gui-item", "unavailable")
                .add(TextRef.translated("integration.settings.title"), "gui-title", "settings")
                .add(TextRef.translated("integration.settings.notifications"), "gui-item", "notifications")
                .add(TextRef.translated("integration.settings.enabled"), "gui-state", "enabled")
                .add(TextRef.translated("integration.settings.disabled"), "gui-state", "disabled")
                .add(TextRef.translated("integration.settings.view"), "gui-item", "view")
                .add(TextRef.translated("integration.settings.compact"), "gui-item", "compact")
                .add(TextRef.translated("integration.settings.detailed"), "gui-item", "detailed")
                .add(TextRef.translated("integration.settings.unsaved"), "gui-item", "dirty")
                .add(TextRef.translated("integration.settings.saved"), "gui-feedback", "saved")
                .add(TextRef.translated("integration.settings.save", "Save configuration"), "gui-item", "save")
                .add(TextRef.translated("integration.settings.reload", "Reload configuration and translations"), "gui-item", "reload")
                .add(TextRef.translated("integration.status.title", "Show application status"), "gui-title", "status")
                .add(TextRef.translated("integration.status.lifecycle"), "gui-item", "lifecycle")
                .add(TextRef.translated("integration.status.config"), "gui-lore", "config")
                .add(TextRef.translated("integration.status.online"), "gui-item", "online")
                .add(TextRef.translated("integration.voucher.name", "Integration voucher"), "gui-item", "voucher")
                .add(TextRef.translated("integration.voucher.lore", "A reusable tagged example item"), "gui-lore", "voucher")
                .add(TextRef.translated("integration.voucher.given", "Tagged voucher added to your inventory."), "command", "voucher-given")
                .add(TextRef.translated("integration.events.join"), "event", "player-join")
                .add(TextRef.translated("integration.command.player-only"), "command", "player-only")
                .add(TextRef.translated("integration.command.denied"), "command", "denied")
                .add(TextRef.translated("integration.command.reload-success"), "command", "reload-success")
                .add(TextRef.translated("integration.command.reload-partial"), "command", "reload-partial")
                .add(TextRef.translated("integration.command.save-success"), "command", "save-success")
                .add(TextRef.translated("integration.command.save-conflict"), "command", "save-conflict")
                .add(TextRef.translated("integration.command.save-failure"), "command", "save-failure")
                .add(TextRef.translated("integration.command.delayed"), "command", "delayed")
                .add(TextRef.translated("integration.command.scheduled"), "command", "scheduled")
                .add(TextRef.translated("integration.command.async-started"), "command", "async-started")
                .add(TextRef.translated("integration.command.async-result"), "command", "async-result")
                .build();
        TextCatalog references = TextCatalog.builder().addCatalog(new IntegrationCommands(null, null, null).definition().textReferences())
                .addCatalog(CommandErrorMessages.defaults().textReferences())
                .addCatalog(new CommandHelpRenderer(new StaticTextProvider(MissingTranslationPolicy.SHOW_KEY)).textReferences())
                .addCatalog(app).build();
        TextCatalogManifest.write(references, Path.of(args[0]));
    }
}
