package com.trickshotmlg.devkit.bukkit.commands;

import com.trickshotmlg.devkit.commands.CommandSender;
import com.trickshotmlg.devkit.commands.SenderKind;
import com.trickshotmlg.devkit.permissions.Permission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Objects;

/** Bridges Bukkit senders to the platform-neutral command contract. */
final class BukkitCommandSenderAdapter {
    private final Locale defaultLocale;
    BukkitCommandSenderAdapter(Locale defaultLocale) { this.defaultLocale = Objects.requireNonNull(defaultLocale, "defaultLocale"); }
    CommandSender adapt(org.bukkit.command.CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        return new BukkitCommandSender() {
            @Override public org.bukkit.command.CommandSender bukkitSender() { return sender; }
            @Override public SenderKind kind() {
                if (sender instanceof Player) return SenderKind.PLAYER;
                if (sender instanceof ConsoleCommandSender) return SenderKind.CONSOLE;
                return SenderKind.OTHER;
            }
            @SuppressWarnings("deprecation")
            @Override public Locale locale() {
                if (!(sender instanceof Player player)) return defaultLocale;
                String tag = player.getLocale().replace('_', '-');
                Locale locale = Locale.forLanguageTag(tag);
                return locale.getLanguage().isEmpty() ? defaultLocale : locale;
            }
            @Override public boolean hasPermission(Permission permission) {
                return sender.hasPermission(Objects.requireNonNull(permission, "permission").node());
            }
            @Override public void sendMessage(Component message) {
                Component nonNullMessage = Objects.requireNonNull(message, "message");
                if (sender instanceof Audience audience) audience.sendMessage(nonNullMessage);
                else sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(nonNullMessage));
            }
        };
    }
}
