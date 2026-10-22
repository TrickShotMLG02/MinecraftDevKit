package com.trickshotmlg.devkit.lifecycle;

import java.util.List;

/** Result of an ordered reload pass. Hooks are independent and are not transactionally rolled back.
 * @param attempted names of hooks that were invoked, in dependency order
 * @param failures failures returned for hooks that threw
 */
public record LifecycleReloadResult(List<String> attempted, List<LifecycleException> failures) {
    public LifecycleReloadResult {
        attempted = List.copyOf(attempted);
        failures = List.copyOf(failures);
    }
    /** Whether every attempted reload hook succeeded. */
    public boolean successful() { return failures.isEmpty(); }
}
