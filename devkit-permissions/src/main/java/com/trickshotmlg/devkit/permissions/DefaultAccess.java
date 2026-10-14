package com.trickshotmlg.devkit.permissions;

/**
 * A platform-neutral description of the default access a compatible platform should apply.
 *
 * <p>This is metadata only. The permissions module does not evaluate players, operators, or
 * platform-specific permission assignments.</p>
 */
public enum DefaultAccess {
    /** Enabled for everyone by default. */
    TRUE,
    /** Disabled for everyone by default. */
    FALSE,
    /** Enabled for operators by default. */
    OP,
    /** Enabled for non-operators by default. */
    NOT_OP
}
