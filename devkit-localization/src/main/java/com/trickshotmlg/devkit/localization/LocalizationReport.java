package com.trickshotmlg.devkit.localization;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable, stably ordered result of scanning declared references against translations. */
public final class LocalizationReport {
    private final List<LocalizationFinding> findings;
    private final Map<String, Integer> counts;
    private final boolean failsBuild;
    LocalizationReport(List<LocalizationFinding> findings, boolean failsBuild) {
        this.findings = findings.stream().sorted(java.util.Comparator
                .comparing((LocalizationFinding f) -> f.severity().ordinal())
                .thenComparing(f -> f.locale().orElse("")).thenComparing(LocalizationFinding::key)
                .thenComparing(f -> f.category().name())).toList();
        TreeMap<String, Integer> totals = new TreeMap<>();
        for (LocalizationFinding.Severity severity : LocalizationFinding.Severity.values()) {
            totals.put(severity.name(), (int) this.findings.stream().filter(f -> f.severity() == severity).count());
        }
        this.counts = java.util.Collections.unmodifiableMap(totals);
        this.failsBuild = failsBuild;
    }
    /** @return findings in severity, locale, key, and category order */
    public List<LocalizationFinding> findings() { return findings; }
    /** @return immutable totals keyed by lower-case severity */
    public Map<String, Integer> counts() { return counts; }
    /** @return whether a finding matched the configured fail-on policy */
    public boolean failsBuild() { return failsBuild; }
}
