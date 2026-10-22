package com.trickshotmlg.devkit.config;

import java.util.List;

/** Indicates that a configuration candidate failed schema validation. */
public final class ConfigValidationException extends IllegalArgumentException {
    private final List<ConfigIssue> issues;
    public ConfigValidationException(List<ConfigIssue> issues) {
        super(issues.stream().filter(issue -> issue.severity() == ConfigIssue.Severity.ERROR)
                .map(issue -> issue.path() + ": " + issue.message()).collect(java.util.stream.Collectors.joining("; ")));
        this.issues = List.copyOf(issues);
    }
    public List<ConfigIssue> issues() { return issues; }
}
