package com.trickshotmlg.devkit.runtimeprobe;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Small deterministic state holder for the test-only, real-player acceptance session. */
final class InteractiveSessionState {
    enum Stage { COMMAND, PERMISSION_DENIED, PERMISSION_GRANTED, GUI_BUTTON, GUI_PROTECTION, COMPLETE, CANCELLED }
    record Assertion(String scenario, String status, String detail, String timestamp) { }

    private final String sessionId = UUID.randomUUID().toString();
    private final String platform;
    private final UUID playerId;
    private final List<Assertion> assertions = new ArrayList<>();
    private final java.util.Set<String> recorded = new java.util.HashSet<>();
    private Stage stage = Stage.COMMAND;

    InteractiveSessionState(String platform, UUID playerId) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
    }

    String sessionId() { return sessionId; }
    String platform() { return platform; }
    UUID playerId() { return playerId; }
    Stage stage() { return stage; }
    List<Assertion> assertions() { return List.copyOf(assertions); }

    boolean record(UUID actor, Stage expected, String scenario, boolean passed, String detail, Stage next) {
        if (!playerId.equals(actor) || stage != expected) return false;
        return observe(actor, scenario, passed, detail, next);
    }

    boolean observe(UUID actor, String scenario, boolean passed, String detail, Stage next) {
        if (!playerId.equals(actor)) return false;
        if (recorded.contains(scenario)) {
            int previous = -1;
            for (int index = 0; index < assertions.size(); index++)
                if (assertions.get(index).scenario().equals(scenario)) { previous = index; break; }
            if (previous >= 0 && "PASS".equals(assertions.get(previous).status())) return false;
            if (previous >= 0) assertions.remove(previous);
        }
        recorded.add(scenario);
        assertions.add(new Assertion(scenario, passed ? "PASS" : "FAIL", detail, Instant.now().toString()));
        if (passed) stage = Objects.requireNonNull(next, "next");
        return true;
    }

    boolean has(String scenario) { return recorded.contains(scenario); }
    void complete() { if (stage != Stage.CANCELLED) stage = Stage.COMPLETE; }

    void cancel(String detail) {
        if (stage == Stage.COMPLETE || stage == Stage.CANCELLED) return;
        assertions.add(new Assertion("session.cancelled", "NOT VERIFIED", detail, Instant.now().toString()));
        stage = Stage.CANCELLED;
    }
}
