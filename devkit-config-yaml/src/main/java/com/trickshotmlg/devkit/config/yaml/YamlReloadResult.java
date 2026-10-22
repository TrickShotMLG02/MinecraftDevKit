package com.trickshotmlg.devkit.config.yaml;

import com.trickshotmlg.devkit.config.ConfigIssue;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Structured result of loading and atomically publishing a YAML configuration. */
public record YamlReloadResult(boolean success, boolean changed, boolean conflict, Path file,
                              long generation, List<ConfigIssue> issues, Optional<String> error) {
    public YamlReloadResult {
        Objects.requireNonNull(file, "file");
        issues = List.copyOf(issues);
        Objects.requireNonNull(error, "error");
    }
}
