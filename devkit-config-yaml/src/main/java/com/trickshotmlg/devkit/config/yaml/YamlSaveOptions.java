package com.trickshotmlg.devkit.config.yaml;

import java.util.Objects;

/** Explicit policies for full-document YAML persistence. Rewriting normalizes comments and formatting. */
public record YamlSaveOptions(ExternalChangePolicy externalChangePolicy, DocumentPolicy documentPolicy) {
    public YamlSaveOptions {
        Objects.requireNonNull(externalChangePolicy, "externalChangePolicy");
        Objects.requireNonNull(documentPolicy, "documentPolicy");
    }
    /** Safe default: refuse to overwrite unexpected external edits and explicitly rewrite YAML formatting. */
    public static YamlSaveOptions safeRewrite() {
        return new YamlSaveOptions(ExternalChangePolicy.REJECT, DocumentPolicy.REWRITE_DOCUMENT);
    }
    public enum ExternalChangePolicy { REJECT, OVERWRITE }
    public enum DocumentPolicy { REWRITE_DOCUMENT }
}
