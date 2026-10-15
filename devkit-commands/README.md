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

The model intentionally leaves parsing, suggestions, execution, help, and sender adaptation to the Cloud-backed runtime and platform adapter.
