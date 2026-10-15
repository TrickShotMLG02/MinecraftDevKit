package com.trickshotmlg.devkit.localization;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Structured outcome from a localization catalog reload. */
public record ReloadResult(boolean successful, boolean snapshotChanged, long generation,
                           List<String> locales, int translationCount, List<String> sources,
                           Optional<String> error) {
    public ReloadResult {
        locales = List.copyOf(locales);
        sources = List.copyOf(sources);
        Objects.requireNonNull(error, "error");
        if (successful && error.isPresent()) throw new IllegalArgumentException("Successful reload cannot contain an error");
        if (!successful && error.isEmpty()) throw new IllegalArgumentException("Failed reload must contain an error");
    }
}
