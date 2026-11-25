package com.trickshotmlg.devkit.bukkit.feedback;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Optional platform transport for native Adventure player feedback. */
public interface FeedbackPlatformExtension {
    void message(Player player, Component component);
    void actionBar(Player player, Component component);
    void title(Player player, Component title, Component subtitle, FeedbackTimings timings);
    void clearTitle(Player player);
}
