package com.trickshotmlg.devkit.commands;

/** Supported platform-independent argument shapes. Parsing is delegated to Cloud adapters. */
public enum ArgumentType {
    WORD,
    GREEDY_STRING,
    INTEGER,
    LONG,
    DOUBLE,
    BOOLEAN,
    ENUM,
    UUID,
    /** Parser supplied to a Cloud-backed runtime. */
    CUSTOM
}
