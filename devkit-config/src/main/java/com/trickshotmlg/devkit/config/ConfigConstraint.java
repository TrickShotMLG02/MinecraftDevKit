package com.trickshotmlg.devkit.config;

import java.util.List;

/** Validates relationships between values in a complete candidate configuration. */
@FunctionalInterface
public interface ConfigConstraint {
    List<ConfigIssue> validate(ConfigValues values);
}
