package com.trickshotmlg.devkit.runtimeprobe;

import java.util.Objects;

record RuntimeCheck(String name, String status, String detail) {
    RuntimeCheck {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(detail, "detail");
    }

    String json() {
        return "{\"name\":\"" + escape(name) + "\",\"status\":\"" + escape(status)
                + "\",\"detail\":\"" + escape(detail) + "\"}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
}
