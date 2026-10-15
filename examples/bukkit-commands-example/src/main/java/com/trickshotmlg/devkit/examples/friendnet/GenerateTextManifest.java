package com.trickshotmlg.devkit.examples.friendnet;

import com.trickshotmlg.devkit.commands.error.CommandErrorMessages;
import com.trickshotmlg.devkit.commands.help.CommandHelpRenderer;
import com.trickshotmlg.devkit.text.MissingTranslationPolicy;
import com.trickshotmlg.devkit.text.StaticTextProvider;
import com.trickshotmlg.devkit.text.TextCatalog;
import com.trickshotmlg.devkit.text.TextRef;
import com.trickshotmlg.devkit.localization.TextCatalogManifest;

import java.nio.file.Path;

/** Generates scanner references from compiled command and application text definitions. */
public final class GenerateTextManifest {
    private GenerateTextManifest() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected generated manifest output path");
        TextCatalog application = TextCatalog.builder()
                .add(TextRef.translated("friends.list", "Page {page}"), "response", "FriendCommands.list")
                .add(TextRef.translated("friends.add.success", "Added {player} as a friend."), "response", "FriendCommands.add")
                .add(TextRef.translated("friends.remove.success", "Removed {player} as a friend."), "response", "FriendCommands.remove")
                .add(TextRef.translated("requests.summary", "Request overview is an example response."), "response", "FriendCommands.requests")
                .add(TextRef.translated("requests.list", "You have no pending requests."), "response", "FriendCommands.requests.list")
                .add(TextRef.translated("requests.accept.success", "Accepted {player}'s request."), "response", "FriendCommands.requests.accept")
                .add(TextRef.translated("requests.accept.all.success", "Accepted all pending requests."), "response", "FriendCommands.requests.accept.all")
                .add(TextRef.translated("requests.deny.success", "Denied {player}'s request."), "response", "FriendCommands.requests.deny")
                .add(TextRef.translated("localization.reload.success", "Reloaded {count} translations across {locales} locales."), "response", "FriendCommands.admin.reload.success")
                .add(TextRef.translated("localization.reload.failure", "Localization reload failed: {error}"), "response", "FriendCommands.admin.reload.failure")
                .build();
        TextCatalog references = TextCatalog.builder()
                .addCatalog(FriendCommands.DEFINITION.textReferences())
                .addCatalog(CommandErrorMessages.defaults().textReferences())
                .addCatalog(new CommandHelpRenderer(new StaticTextProvider(MissingTranslationPolicy.SHOW_KEY))
                        .textReferences())
                .addCatalog(application)
                .build();
        TextCatalogManifest.write(references, Path.of(args[0]));
    }
}
