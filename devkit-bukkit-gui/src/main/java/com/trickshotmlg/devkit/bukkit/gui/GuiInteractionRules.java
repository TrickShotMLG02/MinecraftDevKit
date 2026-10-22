package com.trickshotmlg.devkit.bukkit.gui;

import org.bukkit.event.inventory.InventoryAction;

/** Pure classification rules for protecting managed inventory views. */
final class GuiInteractionRules {
    private GuiInteractionRules() { }

    static boolean ownedBy(Object expectedOwner, Object actualOwner) {
        return expectedOwner == actualOwner;
    }

    static boolean cancelClick(GuiInteractionPolicy policy, int rawSlot, int topSize, InventoryAction action) {
        if (policy == GuiInteractionPolicy.LOCK_ALL || rawSlot >= 0 && rawSlot < topSize) return true;
        return action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.COLLECT_TO_CURSOR;
    }

    static boolean cancelDrag(GuiInteractionPolicy policy, int topSize, Iterable<Integer> rawSlots) {
        if (policy == GuiInteractionPolicy.LOCK_ALL) return true;
        for (int rawSlot : rawSlots) if (rawSlot >= 0 && rawSlot < topSize) return true;
        return false;
    }
}
