package com.trickshotmlg.devkit.config.yaml;

import com.trickshotmlg.devkit.config.ConfigIssue;
import com.trickshotmlg.devkit.config.ConfigStore;
import java.util.List;
import java.util.Map;

/** Successfully decoded YAML configuration and non-fatal findings. */
public record YamlConfigLoadResult(ConfigStore store, Map<String, Object> unknownValues,
                                   List<ConfigIssue> issues, boolean migrated, int sourceVersion) {
    public YamlConfigLoadResult {
        unknownValues = YamlConfigFile.freezeUnknownValues(unknownValues);
        issues = List.copyOf(issues);
    }

    @Override public Map<String, Object> unknownValues() {
        return YamlConfigFile.freezeUnknownValues(unknownValues);
    }
}
