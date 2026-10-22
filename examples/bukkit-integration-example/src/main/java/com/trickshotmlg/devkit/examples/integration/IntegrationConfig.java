package com.trickshotmlg.devkit.examples.integration;

import com.trickshotmlg.devkit.config.ConfigKey;
import com.trickshotmlg.devkit.config.ConfigSchema;

/** Typed configuration schema used by the integration plugin. */
public final class IntegrationConfig {
    public static final ConfigKey<Boolean> NOTIFICATIONS = ConfigKey.bool("features.notifications-enabled", true)
            .description("Whether the example enables notification features").build();
    public static final ConfigKey<Boolean> SHOW_ONLINE_COUNT = ConfigKey.bool("features.show-online-count", true)
            .description("Whether online player counts are displayed").build();
    public static final ConfigKey<Integer> REFRESH_INTERVAL = ConfigKey.integer("gui.refresh-interval-ticks", 40)
            .range(10, 1200).description("Periodic open-menu refresh interval in server ticks").build();
    public static final ConfigSchema SCHEMA = ConfigSchema.builder().version(1)
            .register(NOTIFICATIONS, SHOW_ONLINE_COUNT, REFRESH_INTERVAL).build();
    private IntegrationConfig() { }
}
