package com.trickshotmlg.devkit.config.yaml;

import com.trickshotmlg.devkit.config.ConfigIssue;
import java.io.IOException;
import java.util.List;

/** YAML read or schema-validation failure with structured findings. */
public final class YamlConfigException extends IOException {
    private final List<ConfigIssue> issues;
    public YamlConfigException(String message, List<ConfigIssue> issues) { super(message); this.issues = List.copyOf(issues); }
    public YamlConfigException(String message, Throwable cause, List<ConfigIssue> issues) { super(message, cause); this.issues = List.copyOf(issues); }
    public List<ConfigIssue> issues() { return issues; }
}
