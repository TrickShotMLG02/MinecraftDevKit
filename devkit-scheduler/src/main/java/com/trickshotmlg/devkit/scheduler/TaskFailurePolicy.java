package com.trickshotmlg.devkit.scheduler;

/** Policy for an exception thrown by a repeating task callback. One-shot tasks always fail terminally. */
public enum TaskFailurePolicy {
    /** Report the exception and cancel the repeating task. This is the default. */
    CANCEL_ON_FAILURE,
    /** Report the exception and allow later scheduled invocations to continue. */
    CONTINUE_ON_FAILURE
}
