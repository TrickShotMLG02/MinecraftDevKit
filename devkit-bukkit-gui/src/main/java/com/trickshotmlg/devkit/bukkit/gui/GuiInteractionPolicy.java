package com.trickshotmlg.devkit.bukkit.gui;

/** Controls clicks outside the managed top inventory while a GUI is open. */
public enum GuiInteractionPolicy {
    /** Protect GUI slots and actions that can move items between the GUI and player inventory. */
    PROTECT_GUI,
    /** Cancel every click and drag in the view, including harmless player-inventory interactions. */
    LOCK_ALL
}
