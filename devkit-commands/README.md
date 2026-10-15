# devkit-commands

`devkit-commands` contains the platform-independent, immutable model used to declare command trees. It depends on `devkit-permissions` for typed permission references and `devkit-text` for literal or translated descriptions. It does not contain a Minecraft command parser or server API; Cloud execution is supplied by the integration layer.

## Define a command tree

```java
import com.trickshotmlg.devkit.commands.*;
import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.text.TextRef;

final class FriendCommands {
    static final Argument<String> TARGET = Arguments.word("player");
    static final Argument<Integer> PAGE = Arguments.integer("page", 1, 100);

    static CommandDefinition create(Permission listPermission, Permission addPermission) {
        return CommandRegistry.builder()
            .root("friends", root -> root
                .aliases("friend", "f")
                .description(TextRef.translated(
                    "commands.friends.description", "Manage your friends"))
                .subcommand("list", list -> list
                    .permission(listPermission)
                    .playerOnly()
                    .optional(PAGE, 1)
                    .execute(context -> {
                        int page = context.get(PAGE);
                        // Send the requested page through application services.
                    }))
                .subcommand("add", add -> add
                    .permission(addPermission)
                    .playerOnly()
                    .required(TARGET)
                    .execute(context -> {
                        String target = context.get(TARGET);
                    })))
            .build();
    }
}
```

The `CommandDefinition` is a stable snapshot. Duplicate root names, aliases, sibling literals, invalid names, invalid argument ordering, and misplaced greedy arguments are rejected during `build()`. Literal child names can share a level with an argument branch; Cloud gives literal branches precedence when dispatching that shape.

Permission and sender requirements declared on a parent are inherited by descendants and combined with logical AND. Permission node names do not imply authorization inheritance. Optional arguments with defaults are available through `context.get(handle)`; optional arguments without defaults use `context.getOptional(handle)`.

## Cloud v2 execution

The module includes `CloudCommandRegistrar`, a thin bridge from the immutable definitions to an existing Cloud v2 `CommandManager`. The module publishes Cloud Core 2.1.0 as an API dependency; platform integrations provide the manager and a function that adapts Cloud's native sender to `CommandSender`.

```java
CloudCommandRegistrar<NativeSender> registrar =
    new CloudCommandRegistrar<>(cloudManager, senderAdapter);
registrar.register(FriendCommands.create(listPermission, addPermission));
```

The registrar uses Cloud's standard parsers for the built-in argument types, preserving parser suggestions and range constraints. Custom types use `Arguments.custom("target", Target.class)` and a Cloud `ParserDescriptor` registered through `registrar.parser(handle, descriptor)`. Custom suggestion providers return a `CompletionStage<List<String>>`; Cloud controls when the provider runs and converts the results to platform suggestions.

Each executable command path becomes a Cloud command. Literal child branches and argument branches are registered together, so Cloud's literal-first parsing behavior applies. The bridge attaches one Cloud predicate permission that evaluates every inherited typed permission and sender restriction in the MinecraftDevKit model. This preserves the framework's logical-AND access rules while allowing Cloud and the platform to filter inaccessible commands.

The command handler runs on the execution coordinator configured for the supplied Cloud manager. Suggestions run through Cloud's suggestion pipeline. Bukkit integrations must select a coordinator compatible with the platform's thread rules and should not move Bukkit API access to an arbitrary executor.

Text descriptions are deliberately not copied into Cloud's static descriptions: translated references must be resolved for the current sender when rendering help. The Cloud manager remains available to applications for advanced parsers, preprocessors, exception handling, and other Cloud features.
