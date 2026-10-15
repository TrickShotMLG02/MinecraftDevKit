package com.trickshotmlg.devkit.commands;

import com.trickshotmlg.devkit.permissions.Permission;
import net.kyori.adventure.text.Component;

import java.util.Locale;

/** Platform-neutral view of a command sender supplied by an adapter. */
public interface CommandSender {
    SenderKind kind();
    Locale locale();
    boolean hasPermission(Permission permission);
    void sendMessage(Component message);
}
