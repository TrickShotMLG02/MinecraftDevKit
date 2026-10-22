package com.trickshotmlg.devkit.config.yaml;

import com.trickshotmlg.devkit.config.ConfigIssue;
import com.trickshotmlg.devkit.config.ConfigStore;
import java.util.List;
import java.util.Map;

/** Successfully decoded YAML configuration and non-fatal findings. */
public record YamlConfigLoadResult(ConfigStore store, Map<String, Object> unknownValues,
                                   List<ConfigIssue> issues, boolean migrated, int sourceVersion) {
    public YamlConfigLoadResult {
        unknownValues = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(unknownValues));
        issues = List.copyOf(issues);
    }
}
