package com.trickshotmlg.devkit.lifecycle;

import java.util.List;

/** Result of an ordered reload pass. Reload hooks are independent and are not transactionally rolled back. */
public record LifecycleReloadResult(List<String> attempted, List<LifecycleException> failures) {
    public LifecycleReloadResult {
        attempted = List.copyOf(attempted);
        failures = List.copyOf(failures);
    }
    public boolean successful() { return failures.isEmpty(); }
}
