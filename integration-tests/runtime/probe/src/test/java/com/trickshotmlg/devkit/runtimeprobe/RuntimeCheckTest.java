package com.trickshotmlg.devkit.runtimeprobe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeCheckTest {
    @Test
    void reportEscapesStructuredValues() {
        assertEquals("{\"name\":\"a\\\"b\",\"status\":\"PASS\",\"detail\":\"line1\\nline2\"}",
                new RuntimeCheck("a\"b", "PASS", "line1\nline2").json());
    }
}
