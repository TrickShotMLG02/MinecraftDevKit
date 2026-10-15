package com.trickshotmlg.devkit.localization;

/** Receives structured localization diagnostics without requiring a logging framework. */
@FunctionalInterface
public interface TextDiagnosticSink {
    /** Sink that intentionally discards all events. */
    TextDiagnosticSink NO_OP = diagnostic -> { };

    /**
     * Receives one structured diagnostic event.
     *
     * @param diagnostic event to report
     */
    void report(TextDiagnostic diagnostic);
}
