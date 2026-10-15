package com.trickshotmlg.devkit.examples.friendnet;

import com.trickshotmlg.devkit.commands.error.CommandErrorMessages;
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
                .add(TextRef.translated("admin.reload.success", "FriendNet example data reloaded."), "response", "FriendCommands.admin.reload")
                .build();
        TextCatalog references = TextCatalog.builder()
                .addCatalog(FriendCommands.DEFINITION.textReferences())
                .addCatalog(CommandErrorMessages.defaults().textReferences())
                .addCatalog(application)
                .build();
        TextCatalogManifest.write(references, Path.of(args[0]));
    }
}
