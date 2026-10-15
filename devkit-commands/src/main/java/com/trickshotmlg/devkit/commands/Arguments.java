package com.trickshotmlg.devkit.commands;

import java.util.Optional;
import java.util.UUID;

/** Factories for common typed argument descriptors. Cloud adapters provide the parsers. */
public final class Arguments {
    private Arguments() { }

    public static Argument<String> word(String name) { return create(name, ArgumentType.WORD, String.class); }
    public static Argument<String> greedyString(String name) { return create(name, ArgumentType.GREEDY_STRING, String.class); }
    public static Argument<Integer> integer(String name) { return integer(name, null, null); }
    public static Argument<Integer> integer(String name, Integer minimum, Integer maximum) {
        return new Argument<>(name, ArgumentType.INTEGER, Integer.class, null, opt(minimum), opt(maximum), Optional.empty());
    }
    public static Argument<Long> longInteger(String name) { return longInteger(name, null, null); }
    public static Argument<Long> longInteger(String name, Long minimum, Long maximum) {
        return new Argument<>(name, ArgumentType.LONG, Long.class, null, opt(minimum), opt(maximum), Optional.empty());
    }
    public static Argument<Double> decimal(String name) { return decimal(name, null, null); }
    public static Argument<Double> decimal(String name, Double minimum, Double maximum) {
        return new Argument<>(name, ArgumentType.DOUBLE, Double.class, null, opt(minimum), opt(maximum), Optional.empty());
    }
    public static Argument<Boolean> bool(String name) { return create(name, ArgumentType.BOOLEAN, Boolean.class); }
    public static Argument<UUID> uuid(String name) { return create(name, ArgumentType.UUID, UUID.class); }
    public static <E extends Enum<E>> Argument<E> enumeration(String name, Class<E> enumType) {
        if (enumType == null || !enumType.isEnum()) throw new IllegalArgumentException("enumType must be an enum class");
        return new Argument<>(name, ArgumentType.ENUM, enumType, enumType, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static <T> Argument<T> create(String name, ArgumentType type, Class<T> cls) {
        return new Argument<>(name, type, cls, null, Optional.empty(), Optional.empty(), Optional.empty());
    }
    private static Optional<Number> opt(Number value) { return Optional.ofNullable(value); }
}
