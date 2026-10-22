package com.trickshotmlg.devkit.config.yaml;

import com.trickshotmlg.devkit.config.ConfigIssue;
import com.trickshotmlg.devkit.config.ConfigStore;
import java.util.List;
import java.util.Map;

/** Successfully decoded YAML configuration and non-fatal findings.
 * The source fingerprint is the SHA-256 of the exact bytes parsed, and is empty only for synthetic results.
 */
public record YamlConfigLoadResult(ConfigStore store, Map<String, Object> unknownValues,
                                   List<ConfigIssue> issues, boolean migrated, int sourceVersion,
                                   java.util.Optional<String> sourceFingerprint) {
    /** Preserves the original constructor for callers creating synthetic load results. */
    public YamlConfigLoadResult(ConfigStore store, Map<String, Object> unknownValues,
                               List<ConfigIssue> issues, boolean migrated, int sourceVersion) {
        this(store, unknownValues, issues, migrated, sourceVersion, java.util.Optional.empty());
    }

    public YamlConfigLoadResult {
        unknownValues = YamlConfigFile.freezeUnknownValues(unknownValues);
        issues = List.copyOf(issues);
        sourceFingerprint = java.util.Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
    }

    @Override public Map<String, Object> unknownValues() {
        return YamlConfigFile.freezeUnknownValues(unknownValues);
    }
}
