package com.trickshotmlg.devkit.bukkit.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Zero-based chest slot helpers. Rectangle endpoints are inclusive; menu builders validate the
 * resulting slots against their actual row count. */
public final class Slots {
    private Slots() { }
    public static List<Integer> row(int row) {
        if (row < 0 || row > 5) throw new IllegalArgumentException("Chest row must be between 0 and 5");
        List<Integer> result = new ArrayList<>(9);
        for (int column = 0; column < 9; column++) result.add(row * 9 + column);
        return List.copyOf(result);
    }
    public static List<Integer> column(int column, int rows) {
        if (column < 0 || column > 8) throw new IllegalArgumentException("Chest column must be between 0 and 8");
        checkRows(rows);
        List<Integer> result = new ArrayList<>(rows);
        for (int row = 0; row < rows; row++) result.add(row * 9 + column);
        return List.copyOf(result);
    }
    public static List<Integer> border(int rows) {
        checkRows(rows);
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (int column = 0; column < 9; column++) { result.add(column); result.add((rows - 1) * 9 + column); }
        for (int row = 0; row < rows; row++) { result.add(row * 9); result.add(row * 9 + 8); }
        return List.copyOf(result);
    }
    public static List<Integer> rectangle(int startRow, int startColumn, int endRow, int endColumn) {
        if (startRow < 0 || endRow > 5 || startRow > endRow) throw new IllegalArgumentException("Rectangle rows must be ordered and between 0 and 5");
        if (startColumn < 0 || endColumn > 8 || startColumn > endColumn) throw new IllegalArgumentException("Rectangle columns must be ordered and between 0 and 8");
        List<Integer> result = new ArrayList<>();
        for (int row = startRow; row <= endRow; row++) for (int col = startColumn; col <= endColumn; col++) result.add(row * 9 + col);
        return List.copyOf(result);
    }
    public static List<Integer> of(int... slots) {
        if (slots == null) throw new NullPointerException("slots");
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (int slot : slots) {
            if (slot < 0 || slot > 53) throw new IllegalArgumentException("Slot must be between 0 and 53: " + slot);
            if (!result.add(slot)) throw new IllegalArgumentException("Duplicate slot: " + slot);
        }
        return List.copyOf(result);
    }
    private static void checkRows(int rows) { if (rows < 1 || rows > 6) throw new IllegalArgumentException("Chest rows must be between 1 and 6"); }
}
