package com.trickshotmlg.devkit.config.yaml;

/** Behavior for YAML fields not declared by the Java schema. Unknown values are always retained. */
public enum UnknownKeyPolicy {
    /** Preserve unknown fields and attach warning findings. */ PRESERVE_AND_WARN,
    /** Preserve unknown fields without findings. */ PRESERVE,
    /** Reject the document if any unknown fields are present. */ REJECT
}
