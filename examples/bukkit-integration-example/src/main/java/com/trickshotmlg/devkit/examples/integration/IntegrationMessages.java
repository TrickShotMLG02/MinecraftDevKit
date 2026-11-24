package com.trickshotmlg.devkit.examples.integration;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.Objects;

/** Sends Adventure text through the Bukkit API shared by Spigot and Paper. */
final class IntegrationMessages {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private IntegrationMessages() { }

    static void send(Player player, Component message) {
        Objects.requireNonNull(player, "player").sendMessage(LEGACY.serialize(Objects.requireNonNull(message, "message")));
    }
}
