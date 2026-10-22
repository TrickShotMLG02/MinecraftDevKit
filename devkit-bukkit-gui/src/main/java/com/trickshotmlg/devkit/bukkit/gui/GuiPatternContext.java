package com.trickshotmlg.devkit.bukkit.gui;

/** Immutable inventory coordinates supplied to background patterns. */
public record GuiPatternContext(int slot, int row, int column, int rows, GuiRenderContext renderContext) { }
