package com.trickshotmlg.devkit.runtimeprobe;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InteractiveSessionStateTest {
    private static final UUID OWNER = UUID.randomUUID();

    @Test
    void completeScenarioSetAutomaticallyReachesTerminalPassExactlyOnce() {
        InteractiveSessionState session = session();
        List<String> observed = ids(session);
        for (int index = 0; index < observed.size(); index++) {
            String id = observed.get(index);
            assertTrue(session.observe(OWNER, id, true, "real event observed by controller", session.stage()), id);
            assertEquals(index + 1, session.completedCount());
            assertEquals(index == observed.size() - 1, session.complete());
        }
        assertEquals(InteractiveSessionState.Stage.COMPLETE, session.stage());
        assertEquals("PASS", session.status());
        assertEquals(0, session.remaining().size());
        assertTrue(session.claimCompletionNotification());
        assertFalse(session.claimCompletionNotification());
        assertFalse(session.observe(OWNER, observed.getLast(), true, "late duplicate", session.stage()));
        assertEquals("PASS", session.toJson().contains("\"progress\":{\"completed\":27,\"total\":27,\"failed\":0,\"remaining\":0}")
                ? "PASS" : "INVALID_REPORT");
    }

    @Test
    void reproducesTwentyObservedCallbacksAndNamesWhatIsStillMissing() {
        InteractiveSessionState session = session();
        List<String> seen = List.of(
                "command.root.player", "command.status.player", "command.nested.player", "command.invalid-argument-rejected",
                "command.permission-denied", "command.permission-allowed", "permission.revoked",
                "gui.session-owner", "gui.button", "gui.pagination.next", "gui.pagination.previous", "gui.navigation",
                "gui.toggle.on", "gui.toggle.off", "gui.radio.beta", "gui.confirm", "gui.cancel",
                "inventory.cancelled-click.shift_left", "inventory.cancelled-click.shift_right", "inventory.cancelled-click.number_key");
        assertEquals(20, seen.size());
        seen.forEach(id -> assertTrue(session.observe(OWNER, id, true, "observed interaction", session.stage()), id));

        assertEquals(20, session.completedCount());
        assertEquals(InteractiveSessionState.Stage.GUI, session.stage());
        assertFalse(session.complete());
        assertEquals("IN_PROGRESS", session.status());
        assertTrue(session.remaining().stream().map(InteractiveSessionState.Requirement::id).toList()
                .containsAll(List.of("gui.toggle.count", "gui.radio.alpha", "gui.confirm.once", "gui.cancel-confirm-not-run")));
        assertTrue(session.toJson().contains("\"complete\":false"));
        assertTrue(session.toJson().contains("\"id\":\"gui.radio.alpha\",\"label\":\"Radio selection Alpha\",\"status\":\"PENDING\""));
    }

    @Test
    void reopeningAndDuplicateButtonClicksPreserveProgress() {
        InteractiveSessionState session = session();
        completeCommandsAndPermissions(session);
        assertTrue(session.observe(OWNER, "gui.session-owner", true, "opened", session.stage()));
        assertTrue(session.observe(OWNER, "gui.button", true, "button callback", session.stage()));
        int progress = session.completedCount();
        var stage = session.stage();
        // Reopening the menu is deliberately not a state-reset operation.
        assertFalse(session.observe(OWNER, "gui.button", true, "button clicked again", stage));
        assertEquals(progress, session.completedCount());
        assertEquals(stage, session.stage());
        assertTrue(session.has("gui.button"));
    }

    @Test
    void acceptsGuiRequirementsInAnyOrderButRejectsActionsFromAFutureStage() {
        InteractiveSessionState session = session();
        assertFalse(session.observe(OWNER, "gui.button", true, "stale GUI event", session.stage()));
        assertTrue(session.observe(OWNER, "command.status.player", true, "status command", session.stage()));
        assertTrue(session.observe(OWNER, "command.invalid-argument-rejected", true, "bad arg rejected", session.stage()));
        assertTrue(session.observe(OWNER, "command.root.player", true, "root", session.stage()));
        assertTrue(session.observe(OWNER, "command.nested.player", true, "nested", session.stage()));
        assertEquals(InteractiveSessionState.Stage.PERMISSIONS, session.stage());
        assertFalse(session.canObserve("gui.button"));
        assertTrue(session.canObserve("command.permission-denied"));
    }

    @Test
    void failedRequirementCanBeRetriedAndDoesNotCompleteUntilEveryRequirementPasses() {
        InteractiveSessionState session = session();
        assertTrue(session.observe(OWNER, "command.root.player", false, "callback did not run", session.stage()));
        assertEquals(1, session.failedCount());
        assertEquals("IN_PROGRESS", session.status());
        assertTrue(session.observe(OWNER, "command.root.player", true, "retry callback ran", session.stage()));
        assertEquals(0, session.failedCount());
        assertTrue(session.has("command.root.player"));

        for (String id : ids(session)) if (!session.has(id))
            assertTrue(session.observe(OWNER, id, true, "observed", session.stage()), id);
        assertTrue(session.complete());
        assertFalse(session.observe(OWNER, "inventory.protected-drag-cancelled", false, "late failure", session.stage()));
    }

    @Test
    void cancelIsNotSuccessAndReportRetainsNotVerifiedStatus() {
        InteractiveSessionState session = session();
        session.cancel("player disconnected");
        assertEquals(InteractiveSessionState.Stage.CANCELLED, session.stage());
        assertEquals("NOT VERIFIED", session.status());
        assertFalse(session.complete());
        assertFalse(session.claimCompletionNotification());
        assertTrue(session.toJson().contains("\"status\":\"NOT VERIFIED\""));
        assertTrue(session.toJson().contains("session.cancelled"));
    }

    @Test
    void onlyBoundPlayerCanProgressSession() {
        InteractiveSessionState session = new InteractiveSessionState("spigot", OWNER);
        assertFalse(session.observe(UUID.randomUUID(), "command.root.player", true, "wrong player", session.stage()));
        assertEquals(0, session.completedCount());
    }

    @Test
    void reportEscapesDetailsAndContainsTheTerminalRequirementState() {
        InteractiveSessionState session = session();
        String quote = "quoted \"detail\"\nnext";
        assertTrue(session.observe(OWNER, "command.root.player", true, quote, session.stage()));
        assertTrue(session.toJson().contains("quoted \\\"detail\\\"\\nnext"));
        assertTrue(session.toJson().contains("\"status\":\"IN_PROGRESS\""));
    }

    private static InteractiveSessionState session() { return new InteractiveSessionState("spigot", OWNER); }
    private static void completeCommandsAndPermissions(InteractiveSessionState session) {
        for (String id : List.of("command.root.player", "command.status.player", "command.nested.player",
                "command.invalid-argument-rejected", "command.permission-denied", "command.permission-allowed", "permission.revoked"))
            assertTrue(session.observe(OWNER, id, true, "prerequisite", session.stage()), id);
    }
    private static List<String> ids(InteractiveSessionState session) {
        return session.requirements().stream().map(InteractiveSessionState.Requirement::id).toList();
    }
}
