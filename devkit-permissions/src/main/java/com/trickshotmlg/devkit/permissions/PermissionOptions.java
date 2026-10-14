package com.trickshotmlg.devkit.permissions;

import java.util.Objects;

/** Mutable, short-lived options used only while declaring a {@link Permission}. */
public final class PermissionOptions {
    private String description;
    private DefaultAccess defaultAccess = DefaultAccess.FALSE;
    private boolean frozen;

    PermissionOptions() {
    }

    /**
     * Sets documentation for the permission.
     * @param description non-blank description
     * @return this options object
     */
    public PermissionOptions description(String description) {
        ensureMutable();
        this.description = requireDescription(description);
        return this;
    }

    /**
     * Sets platform-facing default access metadata. Defaults to {@link DefaultAccess#FALSE}.
     * @param defaultAccess access metadata
     * @return this options object
     */
    public PermissionOptions defaultAccess(DefaultAccess defaultAccess) {
        ensureMutable();
        this.defaultAccess = Objects.requireNonNull(defaultAccess, "defaultAccess");
        return this;
    }

    String descriptionValue() {
        return description;
    }

    DefaultAccess defaultAccessValue() {
        return defaultAccess;
    }

    void freeze() {
        frozen = true;
    }

    static String requireDescription(String description) {
        Objects.requireNonNull(description, "description");
        if (description.isBlank()) {
            throw new IllegalArgumentException("Description must not be blank");
        }
        return description;
    }

    private void ensureMutable() {
        if (frozen) {
            throw new IllegalStateException("Permission options can only be changed during declaration");
        }
    }
}
