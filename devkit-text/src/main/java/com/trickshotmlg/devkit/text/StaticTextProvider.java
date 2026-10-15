package com.trickshotmlg.devkit.text;

import net.kyori.adventure.text.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** A provider for applications that do not load translation catalogs. */
public final class StaticTextProvider implements TextProvider {
    private static final Pattern PLACEHOLDER = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]*");

    private final MissingTranslationPolicy missingTranslationPolicy;

    /** Creates a provider that displays an unresolved key when no fallback exists. */
    public StaticTextProvider() {
        this(MissingTranslationPolicy.SHOW_KEY);
    }

    /**
     * Creates a provider using the specified missing-key behavior.
     *
     * @param missingTranslationPolicy behavior when a translation is missing and has no fallback
     */
    public StaticTextProvider(MissingTranslationPolicy missingTranslationPolicy) {
        this.missingTranslationPolicy = Objects.requireNonNull(missingTranslationPolicy, "missingTranslationPolicy");
    }

    /**
     * Returns the missing-key behavior configured for this provider.
     *
     * @return configured missing-key behavior
     */
    public MissingTranslationPolicy missingTranslationPolicy() {
        return missingTranslationPolicy;
    }

    @Override
    public Component resolve(TextRef reference, Locale locale, Map<String, ?> placeholders) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(placeholders, "placeholders");
        validatePlaceholders(placeholders);

        String value;
        if (reference instanceof TextRef.Literal literal) {
            value = literal.value();
        } else if (reference instanceof TextRef.Translated translated) {
            value = translated.fallback().orElseGet(() -> missingValue(translated.key(), locale));
        } else {
            throw new IllegalArgumentException("Unsupported text reference type: " + reference.getClass().getName());
        }
        return interpolate(value, placeholders);
    }

    private String missingValue(String key, Locale locale) {
        return switch (missingTranslationPolicy) {
            case SHOW_KEY -> key;
            case SHOW_MARKER -> "<missing:" + key + ">";
            case ERROR -> throw new MissingTranslationException(key, locale);
        };
    }

    private static void validatePlaceholders(Map<String, ?> placeholders) {
        placeholders.forEach((name, value) -> {
            Objects.requireNonNull(name, "placeholder name");
            if (!PLACEHOLDER.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid placeholder name: " + name);
            }
            Objects.requireNonNull(value, "placeholder value for " + name);
        });
    }

    private static Component interpolate(String template, Map<String, ?> placeholders) {
        if (placeholders.isEmpty()) {
            return Component.text(unescapeBraces(template));
        }

        Component result = Component.empty();
        StringBuilder literal = new StringBuilder();
        for (int index = 0; index < template.length();) {
            char current = template.charAt(index);
            if (current == '{' && index + 1 < template.length() && template.charAt(index + 1) == '{') {
                literal.append('{');
                index += 2;
                continue;
            }
            if (current == '}' && index + 1 < template.length() && template.charAt(index + 1) == '}') {
                literal.append('}');
                index += 2;
                continue;
            }
            if (current == '{') {
                int closing = template.indexOf('}', index + 1);
                if (closing > index + 1) {
                    String name = template.substring(index + 1, closing);
                    if (PLACEHOLDER.matcher(name).matches() && placeholders.containsKey(name)) {
                        result = appendLiteral(result, literal);
                        Object placeholderValue = Objects.requireNonNull(placeholders.get(name), "placeholder value for " + name);
                        result = result.append(Component.text(String.valueOf(placeholderValue)));
                        index = closing + 1;
                        continue;
                    }
                }
            }
            literal.append(current);
            index++;
        }
        return appendLiteral(result, literal);
    }

    private static Component appendLiteral(Component component, StringBuilder literal) {
        if (literal.isEmpty()) {
            return component;
        }
        Component result = component.append(Component.text(literal.toString()));
        literal.setLength(0);
        return result;
    }

    private static String unescapeBraces(String value) {
        return value.replace("{{", "{").replace("}}", "}");
    }
}
