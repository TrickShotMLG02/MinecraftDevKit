package com.trickshotmlg.devkit.runtimeprobe;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Deterministic state and requirements for a test-only, real-player acceptance session. */
final class InteractiveSessionState {
    enum Stage { COMMANDS, PERMISSIONS, GUI, INVENTORY_PROTECTION, COMPLETE, CANCELLED }
    record Requirement(String id, String label, Stage stage, String instruction) { }
    record Assertion(String scenario, String status, String detail, String timestamp) { }

    private static final List<Requirement> REQUIREMENTS = List.of(
            requirement("command.root.player", "Player root command", Stage.COMMANDS, "Run /mdkruntimeprobe."),
            requirement("command.status.player", "Player status subcommand", Stage.COMMANDS, "Run /mdkruntimeprobe status."),
            requirement("command.nested.player", "Typed nested command", Stage.COMMANDS, "Run /mdkruntimeprobe nested 42."),
            requirement("command.invalid-argument-rejected", "Invalid typed argument rejected", Stage.COMMANDS, "Run /mdkruntimeprobe nested nope."),
            requirement("command.permission-denied", "Protected command denied", Stage.PERMISSIONS, "Run /mdkacceptcloud protected before granting permission."),
            requirement("command.permission-allowed", "Protected command allowed", Stage.PERMISSIONS, "Run /devkitaccept grant, then /mdkacceptcloud protected."),
            requirement("permission.revoked", "Temporary permission revoked", Stage.PERMISSIONS, "This is recorded after the granted protected command."),
            requirement("gui.session-owner", "GUI session belongs to player", Stage.GUI, "Run /devkitaccept gui."),
            requirement("gui.button", "Button callback", Stage.GUI, "In the GUI, click the emerald button."),
            requirement("gui.navigation", "Child navigation and Back", Stage.GUI, "Open the child menu and click Back."),
            requirement("gui.toggle.on", "Toggle enabled", Stage.GUI, "Click the toggle once to turn it on."),
            requirement("gui.toggle.off", "Toggle disabled", Stage.GUI, "Click the toggle a second time to turn it off."),
            requirement("gui.toggle.count", "Exactly two toggle callbacks", Stage.GUI, "Use exactly two toggle clicks."),
            requirement("gui.radio.beta", "Radio selection Beta", Stage.GUI, "Select Beta."),
            requirement("gui.radio.alpha", "Radio selection Alpha", Stage.GUI, "Then select Alpha."),
            requirement("gui.confirm", "Confirmation callback", Stage.GUI, "Open the confirmation menu and confirm once."),
            requirement("gui.confirm.once", "Confirmation callback exactly once", Stage.GUI, "Confirm exactly once."),
            requirement("gui.cancel", "Cancellation callback", Stage.GUI, "Open /devkitaccept cancelgui and click Cancel."),
            requirement("gui.cancel-confirm-not-run", "Cancellation did not confirm", Stage.GUI, "The cancellation dialog must not invoke Confirm."),
            requirement("gui.pagination.next", "Pagination next", Stage.GUI, "Open pagination and click Next; Entry 46 should lead."),
            requirement("gui.pagination.previous", "Pagination previous", Stage.GUI, "Click Previous; Entry 1 should lead."),
            requirement("inventory.cancelled-click.shift_left", "Shift-left protection", Stage.INVENTORY_PROTECTION, "With the managed GUI open, shift-left-click a player item."),
            requirement("inventory.cancelled-click.shift_right", "Shift-right protection", Stage.INVENTORY_PROTECTION, "With the managed GUI open, shift-right-click a player item."),
            requirement("inventory.cancelled-click.number_key", "Number-key swap protection", Stage.INVENTORY_PROTECTION, "Use a number key over a managed GUI slot."),
            requirement("inventory.cancelled-click.double_click", "Double-click collection protection", Stage.INVENTORY_PROTECTION, "With the paper item on the cursor, double-click in the managed GUI."),
            requirement("inventory.cancelled-click.swap_offhand", "Offhand swap protection", Stage.INVENTORY_PROTECTION, "Use the offhand swap key over a managed GUI slot."),
            requirement("inventory.protected-drag-cancelled", "Cross-inventory drag protection", Stage.INVENTORY_PROTECTION, "Drag across the GUI and player-inventory boundary."));

    private final String sessionId = UUID.randomUUID().toString();
    private final String platform;
    private final UUID playerId;
    private final Map<String, Assertion> outcomes = new LinkedHashMap<>();
    private boolean cancelled;
    private boolean completionNotified;

    InteractiveSessionState(String platform, UUID playerId) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
    }

    String sessionId() { return sessionId; }
    String platform() { return platform; }
    UUID playerId() { return playerId; }
    Stage stage() {
        if (cancelled) return Stage.CANCELLED;
        if (complete()) return Stage.COMPLETE;
        return REQUIREMENTS.stream().filter(requirement -> !passed(requirement.id()))
                .findFirst().map(Requirement::stage).orElse(Stage.COMPLETE);
    }
    List<Assertion> assertions() { return List.copyOf(outcomes.values()); }
    List<Requirement> requirements() { return REQUIREMENTS; }
    Requirement requirement(String id) { return REQUIREMENTS.stream().filter(item -> item.id().equals(id)).findFirst().orElse(null); }
    int completedCount() { return (int) outcomes.values().stream().filter(result -> "PASS".equals(result.status())).count(); }
    int failedCount() { return (int) outcomes.values().stream().filter(result -> "FAIL".equals(result.status())).count(); }
    int totalCount() { return REQUIREMENTS.size(); }
    boolean complete() { return !cancelled && completedCount() == REQUIREMENTS.size() && failedCount() == 0; }
    String status() { return complete() ? "PASS" : cancelled ? "NOT VERIFIED" : "IN_PROGRESS"; }
    boolean has(String scenario) { return passed(scenario); }
    List<Requirement> remaining() { return REQUIREMENTS.stream().filter(item -> !passed(item.id())).toList(); }
    List<Requirement> failed() { return REQUIREMENTS.stream().filter(item -> outcomes.containsKey(item.id())
            && "FAIL".equals(outcomes.get(item.id()).status())).toList(); }
    Requirement nextRequirement() { return remaining().stream().findFirst().orElse(null); }
    boolean canObserve(String scenario) {
        if (cancelled || complete()) return false;
        Stage current = stage();
        return REQUIREMENTS.stream().anyMatch(item -> item.id().equals(scenario)
                && (item.stage() == current || (current == Stage.GUI && item.stage() == Stage.INVENTORY_PROTECTION)));
    }
    boolean claimCompletionNotification() {
        if (!complete() || completionNotified) return false;
        completionNotified = true;
        return true;
    }

    boolean record(UUID actor, Stage expected, String scenario, boolean passed, String detail, Stage ignoredNext) {
        if (!playerId.equals(actor) || stage() != expected) return false;
        return observe(actor, scenario, passed, detail, stage());
    }

    boolean observe(UUID actor, String scenario, boolean passed, String detail, Stage ignoredNext) {
        if (!playerId.equals(actor) || !canObserve(scenario)) return false;
        Assertion previous = outcomes.get(scenario);
        if (previous != null && "PASS".equals(previous.status())) return false;
        outcomes.put(scenario, new Assertion(scenario, passed ? "PASS" : "FAIL", detail, Instant.now().toString()));
        return true;
    }

    /** Updates a derived count assertion; unlike an event assertion, a later invalid count must revoke it. */
    boolean observeCurrent(UUID actor, String scenario, boolean passed, String detail) {
        if (!playerId.equals(actor) || !canObserve(scenario)) return false;
        Assertion previous = outcomes.get(scenario);
        if (previous != null && previous.status().equals(passed ? "PASS" : "FAIL")) return false;
        outcomes.put(scenario, new Assertion(scenario, passed ? "PASS" : "FAIL", detail, Instant.now().toString()));
        return true;
    }

    void cancel(String detail) {
        if (complete() || cancelled) return;
        cancelled = true;
        outcomes.put("session.cancelled", new Assertion("session.cancelled", "NOT VERIFIED", detail, Instant.now().toString()));
    }

    String toJson() {
        String assertionsJson = String.join(",", assertions().stream().map(item ->
                "{\"scenario\":\"" + escape(item.scenario()) + "\",\"status\":\"" + item.status()
                        + "\",\"detail\":\"" + escape(item.detail()) + "\",\"timestamp\":\"" + item.timestamp() + "\"}").toList());
        String requirementsJson = String.join(",", REQUIREMENTS.stream().map(item -> {
            Assertion result = outcomes.get(item.id());
            return "{\"id\":\"" + escape(item.id()) + "\",\"label\":\"" + escape(item.label())
                    + "\",\"status\":\"" + (result == null ? "PENDING" : result.status()) + "\"}";
        }).toList());
        return "{\"schemaVersion\":1,\"sessionId\":\"" + sessionId + "\",\"platform\":\"" + escape(platform)
                + "\",\"playerUuid\":\"" + playerId + "\",\"complete\":" + complete() + ",\"status\":\"" + status()
                + "\",\"stage\":\"" + stage() + "\",\"progress\":{\"completed\":" + completedCount()
                + ",\"total\":" + totalCount() + ",\"failed\":" + failedCount() + ",\"remaining\":" + remaining().size()
                + "},\"requirements\":[" + requirementsJson + "],\"assertions\":[" + assertionsJson + "]}";
    }

    private boolean passed(String scenario) {
        Assertion result = outcomes.get(scenario);
        return result != null && "PASS".equals(result.status());
    }

    private static Requirement requirement(String id, String label, Stage stage, String instruction) {
        return new Requirement(id, label, stage, instruction);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                .replace("\r", "\\r").replace("\t", "\\t");
    }
}
