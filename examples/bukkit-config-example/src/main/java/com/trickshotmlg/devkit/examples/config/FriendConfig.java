package com.trickshotmlg.devkit.examples.config;

import com.trickshotmlg.devkit.config.ConfigKey;
import com.trickshotmlg.devkit.config.ConfigSchema;
import java.time.Duration;

/** Java source of truth for this example's configuration schema. */
public final class FriendConfig {
    public static final ConfigKey<Integer> MAX_FRIENDS = ConfigKey.integer("friends.max", 100)
            .range(1, 1000).description("Maximum number of friends per player").build();
    public static final ConfigKey<Boolean> REQUESTS_ENABLED = ConfigKey.bool("friends.requests.enabled", true)
            .description("Whether friend requests are enabled").build();
    public static final ConfigKey<Duration> REQUEST_TIMEOUT = ConfigKey.duration("friends.requests.timeout", Duration.ofMinutes(5))
            .validate(value -> value.isZero() || value.isNegative()
                    ? java.util.Optional.of("must be positive") : java.util.Optional.empty())
            .description("How long a friend request remains valid").build();
    public static final ConfigSchema SCHEMA = ConfigSchema.builder().version(2)
            .register(MAX_FRIENDS, REQUESTS_ENABLED, REQUEST_TIMEOUT)
            .validate(values -> values.get(REQUESTS_ENABLED) && values.get(REQUEST_TIMEOUT).isNegative()
                    ? java.util.List.of(com.trickshotmlg.devkit.config.ConfigIssue.error(REQUEST_TIMEOUT.path(), "must be positive when requests are enabled"))
                    : java.util.List.of())
            .build();
    private FriendConfig() { }
}
