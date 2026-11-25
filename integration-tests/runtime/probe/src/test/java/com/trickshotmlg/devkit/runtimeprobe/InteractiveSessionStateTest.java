package com.trickshotmlg.devkit.runtimeprobe;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InteractiveSessionStateTest {
    @Test
    void bindsAssertionsToPlayerAndRejectsDuplicates() {
        UUID owner = UUID.randomUUID();
        InteractiveSessionState session = new InteractiveSessionState("spigot", owner);
        assertFalse(session.observe(UUID.randomUUID(), "gui.button", true, "wrong player", session.stage()));
        assertTrue(session.observe(owner, "gui.button", true, "client click callback", session.stage()));
        assertFalse(session.observe(owner, "gui.button", true, "duplicate", session.stage()));
        assertEquals(1, session.assertions().size());
        assertEquals("PASS", session.assertions().get(0).status());
    }

    @Test
    void commandFlowRejectsUnexpectedStageAndCanBeCancelled() {
        UUID owner = UUID.randomUUID();
        InteractiveSessionState session = new InteractiveSessionState("paper", owner);
        assertFalse(session.record(owner, InteractiveSessionState.Stage.PERMISSION_DENIED,
                "permission.denied", true, "unexpected", InteractiveSessionState.Stage.PERMISSION_GRANTED));
        assertTrue(session.record(owner, InteractiveSessionState.Stage.COMMAND,
                "command.nested.player", true, "Cloud handler", InteractiveSessionState.Stage.PERMISSION_DENIED));
        assertEquals(InteractiveSessionState.Stage.PERMISSION_DENIED, session.stage());
        session.cancel("disconnect");
        assertEquals(InteractiveSessionState.Stage.CANCELLED, session.stage());
        assertEquals("NOT VERIFIED", session.assertions().get(1).status());
    }

    @Test
    void sessionIdsAndViewerIdentityAreIndependent() {
        var first = new InteractiveSessionState("spigot", UUID.randomUUID());
        var second = new InteractiveSessionState("spigot", UUID.randomUUID());
        assertNotEquals(first.sessionId(), second.sessionId());
        assertNotEquals(first.playerId(), second.playerId());
        assertEquals("spigot", first.platform());
    }

    @Test
    void failedObservationCanBeRetriedButPassedObservationIsIdempotent() {
        UUID owner = UUID.randomUUID();
        InteractiveSessionState session = new InteractiveSessionState("paper", owner);
        assertTrue(session.observe(owner, "inventory.drag", false, "cursor changed", session.stage()));
        assertEquals(InteractiveSessionState.Stage.COMMAND, session.stage());
        assertTrue(session.observe(owner, "inventory.drag", true, "retry preserved inventory", session.stage()));
        assertFalse(session.observe(owner, "inventory.drag", false, "duplicate event", session.stage()));
        assertEquals(1, session.assertions().size());
        assertEquals("PASS", session.assertions().get(0).status());
    }
}
