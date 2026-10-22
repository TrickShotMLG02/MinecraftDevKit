package com.trickshotmlg.devkit.bukkit.gui;

import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GuiInteractionRulesTest {
    @Test
    void ownerIdentityPreventsOneManagerHandlingAnotherManagersView() {
        Object first = new Object();
        Object second = new Object();
        assertTrue(GuiInteractionRules.ownedBy(first, first));
        assertFalse(GuiInteractionRules.ownedBy(first, second));
    }

    @Test
    void protectedGuiCancelsTopAndTransferActionsButAllowsOrdinaryBottomClicks() {
        for (InventoryAction action : InventoryAction.values()) {
            boolean canAffectTop = action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || action == InventoryAction.COLLECT_TO_CURSOR;
            assertEquals(canAffectTop,
                    GuiInteractionRules.cancelClick(GuiInteractionPolicy.PROTECT_GUI, 31, 27, action),
                    "Unexpected bottom-slot policy for " + action);
        }
        assertTrue(GuiInteractionRules.cancelClick(GuiInteractionPolicy.LOCK_ALL, 31, 27, InventoryAction.PICKUP_ALL));
        for (InventoryAction action : InventoryAction.values()) {
            assertTrue(GuiInteractionRules.cancelClick(GuiInteractionPolicy.PROTECT_GUI, 4, 27, action),
                    "Top slot should be protected for " + action);
        }
    }

    @Test
    void dragsAffectingAnyTopSlotAreCancelled() {
        assertTrue(GuiInteractionRules.cancelDrag(GuiInteractionPolicy.PROTECT_GUI, 27, Set.of(4, 27, 28)));
        assertFalse(GuiInteractionRules.cancelDrag(GuiInteractionPolicy.PROTECT_GUI, 27, Set.of(27, 28)));
        assertTrue(GuiInteractionRules.cancelDrag(GuiInteractionPolicy.LOCK_ALL, 27, Set.of(27, 28)));
        assertFalse(GuiInteractionRules.cancelDrag(GuiInteractionPolicy.PROTECT_GUI, 27, Set.of()));
    }
}
