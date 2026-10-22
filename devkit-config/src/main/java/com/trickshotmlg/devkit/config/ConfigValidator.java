package com.trickshotmlg.devkit.config;

import java.util.Optional;

/** Validates one decoded value and returns a safe explanation when it is invalid. */
@FunctionalInterface
public interface ConfigValidator<T> {
    Optional<String> validate(T value);
}
