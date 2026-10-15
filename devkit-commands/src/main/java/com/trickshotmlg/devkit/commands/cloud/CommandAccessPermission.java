package com.trickshotmlg.devkit.commands.cloud;

import com.trickshotmlg.devkit.commands.CommandNode;
import com.trickshotmlg.devkit.commands.CommandSender;
import com.trickshotmlg.devkit.commands.SenderKind;
import com.trickshotmlg.devkit.commands.SenderRequirement;
import org.incendo.cloud.permission.PermissionResult;
import org.incendo.cloud.permission.PredicatePermission;

import java.util.Objects;
import java.util.function.Function;

/** Cloud permission bridge retaining enough metadata to classify framework sender restrictions. */
public final class CommandAccessPermission<C> implements PredicatePermission<C> {
    private final CommandNode node;
    private final Function<C, CommandSender> senderAdapter;

    CommandAccessPermission(CommandNode node, Function<C, CommandSender> senderAdapter) {
        this.node = Objects.requireNonNull(node, "node");
        this.senderAdapter = Objects.requireNonNull(senderAdapter, "senderAdapter");
    }

    @Override public PermissionResult testPermission(C sender) {
        return PermissionResult.of(node.isVisibleTo(Objects.requireNonNull(senderAdapter.apply(sender), "senderAdapter result")), this);
    }

    public com.trickshotmlg.devkit.commands.error.CommandErrorCategory failureCategory(CommandSender sender) {
        if (node.permissionRequirements().stream().anyMatch(permission -> !sender.hasPermission(permission))) {
            return com.trickshotmlg.devkit.commands.error.CommandErrorCategory.NO_PERMISSION;
        }
        for (SenderRequirement requirement : node.senderRequirements()) {
            if (!requirement.test(sender)) {
                if (requirement instanceof SenderRequirement.Kind kind) {
                    return kind.expected() == SenderKind.PLAYER
                            ? com.trickshotmlg.devkit.commands.error.CommandErrorCategory.PLAYER_ONLY
                            : com.trickshotmlg.devkit.commands.error.CommandErrorCategory.CONSOLE_ONLY;
                }
                return com.trickshotmlg.devkit.commands.error.CommandErrorCategory.NO_PERMISSION;
            }
        }
        return com.trickshotmlg.devkit.commands.error.CommandErrorCategory.NO_PERMISSION;
    }
}
