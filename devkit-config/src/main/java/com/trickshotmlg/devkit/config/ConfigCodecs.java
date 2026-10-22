package com.trickshotmlg.devkit.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Reusable codecs for common configuration values. */
public final class ConfigCodecs {
    public static final ConfigCodec<String> STRING = simple("string", String.class, value -> value);
    public static final ConfigCodec<Boolean> BOOLEAN = simple("boolean", Boolean.class, value -> value);
    public static final ConfigCodec<Integer> INTEGER = number("integer", Integer.class, BigDecimal::intValueExact);
    public static final ConfigCodec<Long> LONG = number("long", Long.class, BigDecimal::longValueExact);
    public static final ConfigCodec<Double> DOUBLE = number("finite double", Double.class, value -> {
        double result = value.doubleValue();
        if (!Double.isFinite(result)) throw new IllegalArgumentException("must be finite");
        return result;
    });
    public static final ConfigCodec<Float> FLOAT = number("finite float", Float.class, value -> {
        float result = value.floatValue();
        if (!Float.isFinite(result)) throw new IllegalArgumentException("must be finite");
        return result;
    });
    public static final ConfigCodec<UUID> UUID_CODEC = new ConfigCodec<>() {
        public String description() { return "UUID"; }
        public UUID decode(Object value) {
            if (value instanceof UUID uuid) return uuid;
            if (!(value instanceof String text)) throw expected("UUID string", value);
            try { return UUID.fromString(text); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("invalid UUID: " + text, exception); }
        }
        public Object encode(UUID value) { return Objects.requireNonNull(value, "value").toString(); }
    };
    public static final ConfigCodec<Duration> DURATION = new ConfigCodec<>() {
        public String description() { return "ISO-8601 duration"; }
        public Duration decode(Object value) {
            if (value instanceof Duration duration) return duration;
            if (!(value instanceof String text)) throw expected("ISO-8601 duration string", value);
            try { return Duration.parse(text); }
            catch (RuntimeException exception) { throw new IllegalArgumentException("invalid ISO-8601 duration: " + text, exception); }
        }
        public Object encode(Duration value) { return Objects.requireNonNull(value, "value").toString(); }
    };

    private ConfigCodecs() { }

    /** Creates a codec for an enum using case-sensitive Java enum names. */
    public static <E extends Enum<E>> ConfigCodec<E> enumCodec(Class<E> type) {
        Objects.requireNonNull(type, "type");
        return new ConfigCodec<>() {
            public String description() { return type.getSimpleName() + " enum name"; }
            public E decode(Object value) {
                if (type.isInstance(value)) return type.cast(value);
                if (!(value instanceof String text)) throw expected(description(), value);
                try { return Enum.valueOf(type, text); }
                catch (IllegalArgumentException exception) { throw new IllegalArgumentException("unknown " + description() + ": " + text, exception); }
            }
            public Object encode(E value) { return Objects.requireNonNull(value, "value").name(); }
        };
    }

    /** Creates an immutable list codec. */
    public static <T> ConfigCodec<List<T>> list(ConfigCodec<T> elementCodec) {
        Objects.requireNonNull(elementCodec, "elementCodec");
        return new ConfigCodec<>() {
            public String description() { return "list of " + elementCodec.description(); }
            public List<T> decode(Object value) {
                if (!(value instanceof List<?> input)) throw expected(description(), value);
                ArrayList<T> result = new ArrayList<>(input.size());
                for (int i = 0; i < input.size(); i++) {
                    try { result.add(freeze(elementCodec.decode(input.get(i)))); }
                    catch (RuntimeException exception) { throw new IllegalArgumentException("invalid list element at index " + i + ": " + exception.getMessage(), exception); }
                }
                return List.copyOf(result);
            }
            public Object encode(List<T> value) {
                ArrayList<Object> result = new ArrayList<>();
                for (T item : Objects.requireNonNull(value, "value")) result.add(elementCodec.encode(item));
                return List.copyOf(result);
            }
        };
    }

    /** Creates an immutable string-keyed map codec. */
    public static <T> ConfigCodec<Map<String, T>> map(ConfigCodec<T> valueCodec) {
        Objects.requireNonNull(valueCodec, "valueCodec");
        return new ConfigCodec<>() {
            public String description() { return "map of string to " + valueCodec.description(); }
            public Map<String, T> decode(Object value) {
                if (!(value instanceof Map<?, ?> input)) throw expected(description(), value);
                LinkedHashMap<String, T> result = new LinkedHashMap<>();
                input.forEach((key, raw) -> {
                    if (!(key instanceof String name)) throw new IllegalArgumentException("map keys must be strings");
                    result.put(name, freeze(valueCodec.decode(raw)));
                });
                return Collections.unmodifiableMap(result);
            }
            public Object encode(Map<String, T> value) {
                LinkedHashMap<String, Object> result = new LinkedHashMap<>();
                Objects.requireNonNull(value, "value").forEach((key, item) -> result.put(key, valueCodec.encode(item)));
                return Collections.unmodifiableMap(result);
            }
        };
    }

    /** Creates an application-defined codec. */
    public static <T> ConfigCodec<T> of(String description, Decoder<T> decoder, Encoder<T> encoder) {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(decoder, "decoder");
        Objects.requireNonNull(encoder, "encoder");
        if (description.isBlank()) throw new IllegalArgumentException("description must not be blank");
        return new ConfigCodec<>() {
            public String description() { return description; }
            public T decode(Object value) { return Objects.requireNonNull(decoder.decode(value), "codec returned null"); }
            public Object encode(T value) { return encoder.encode(Objects.requireNonNull(value, "value")); }
        };
    }

    private static <T> ConfigCodec<T> simple(String description, Class<T> type, java.util.function.Function<T, T> identity) {
        return of(description, value -> {
            if (!type.isInstance(value)) throw expected(description, value);
            return identity.apply(type.cast(value));
        }, value -> value);
    }

    private static <T> ConfigCodec<T> number(String description, Class<T> type,
                                              java.util.function.Function<BigDecimal, T> convert) {
        return of(description, value -> {
            if (!(value instanceof Number number)) throw expected(description, value);
            BigDecimal decimal;
            try {
                if (number instanceof BigDecimal bd) decimal = bd;
                else if (number instanceof BigInteger bi) decimal = new BigDecimal(bi);
                else if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) decimal = BigDecimal.valueOf(number.longValue());
                else decimal = new BigDecimal(number.toString());
                return convert.apply(decimal);
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new IllegalArgumentException("value is outside the exact " + description + " range or has a fractional part: " + number, exception);
            }
        }, value -> {
            if (!type.isInstance(value)) throw expected(description, value);
            return value;
        });
    }

    private static IllegalArgumentException expected(String expected, Object value) {
        return new IllegalArgumentException("expected " + expected + ", got " + (value == null ? "null" : value.getClass().getSimpleName()));
    }

    @SuppressWarnings("unchecked")
    static <T> T freeze(T value) {
        if (value instanceof List<?> list) return (T) list.stream().map(ConfigCodecs::freeze).toList();
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(key, freeze(item)));
            return (T) Collections.unmodifiableMap(copy);
        }
        if (value instanceof java.util.Set<?> set) return (T) Collections.unmodifiableSet(new java.util.LinkedHashSet<>(set));
        return value;
    }

    @FunctionalInterface public interface Decoder<T> { T decode(Object value); }
    @FunctionalInterface public interface Encoder<T> { Object encode(T value); }
}
