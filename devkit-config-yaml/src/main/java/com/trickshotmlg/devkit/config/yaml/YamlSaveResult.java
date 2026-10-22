package com.trickshotmlg.devkit.config.yaml;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Result of a configuration save attempt. */
public record YamlSaveResult(boolean success, boolean written, boolean conflict, Path file,
                             long savedGeneration, long currentGeneration, Optional<String> error) {
    public YamlSaveResult {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(error, "error");
        if (success && error.isPresent()) throw new IllegalArgumentException("successful result cannot contain an error");
    }
}
