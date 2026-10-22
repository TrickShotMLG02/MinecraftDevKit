package com.trickshotmlg.devkit.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigSchemaTest {
    @Test void schemaOrdersKeysAndRejectsDuplicatesAndValueSectionConflicts() {
        ConfigKey<Integer> b = ConfigKey.integer("z.b", 1).build();
        ConfigKey<String> a = ConfigKey.string("a", "x").build();
        ConfigSchema schema = ConfigSchema.builder().register(b).register(a).build();
        assertEquals(java.util.List.of("a", "z.b"), schema.keys().keySet().stream().toList());
        assertThrows(IllegalArgumentException.class, () -> ConfigSchema.builder().register(a).register(a));
        assertThrows(IllegalArgumentException.class, () -> ConfigSchema.builder()
                .register(ConfigKey.string("friends", "x").build())
                .register(ConfigKey.integer("friends.max", 1).build()).build());
    }
}
