package com.trickshotmlg.devkit.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigKeyTest {
    @Test void pathsDefaultsAndMetadataAreValidatedAndImmutable() {
        ConfigKey<Integer> key = ConfigKey.integer("friends.max", 10).range(1, 20)
                .description("Maximum friends").sensitive().build();
        assertEquals("friends.max", key.path());
        assertEquals(10, key.defaultValue().orElseThrow());
        assertTrue(key.sensitive());
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.string("a..b", "x").build());
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.integer("x", 0).range(1, 4).build());
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.string("config-version", "x").build());
    }

    @Test void codecsRejectTruncationOverflowAndInvalidValues() {
        assertEquals(2, ConfigCodecs.INTEGER.decode(2L));
        assertThrows(IllegalArgumentException.class, () -> ConfigCodecs.INTEGER.decode(2.5d));
        assertThrows(IllegalArgumentException.class, () -> ConfigCodecs.INTEGER.decode(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> ConfigCodecs.DOUBLE.decode(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> ConfigCodecs.DOUBLE.decode(new java.math.BigDecimal("1e-400")));
        assertThrows(IllegalArgumentException.class, () -> ConfigCodecs.FLOAT.decode(new java.math.BigDecimal("1e-100")));
        assertEquals(Duration.ofMinutes(5), ConfigCodecs.DURATION.decode("PT5M"));
        assertEquals(List.of("a", "b"), ConfigCodecs.list(ConfigCodecs.STRING).decode(List.of("a", "b")));
        Map<String, String> values = ConfigCodecs.map(ConfigCodecs.STRING).decode(Map.of("a", "b"));
        assertThrows(UnsupportedOperationException.class, () -> values.put("x", "y"));
    }
}
