package com.trickshotmlg.devkit.text;

import net.kyori.adventure.text.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Utilities for safe named-placeholder formatting of literal text. */
public final class TextFormatter {
    private static final Pattern PLACEHOLDER_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]*");

    private TextFormatter() {
    }

    /**
     * Returns placeholder names in first-occurrence order. Doubled braces are treated as escaped.
     *
     * @param template text template
     * @return immutable ordered set of names
     */
    public static Set<String> placeholders(String template) {
        Objects.requireNonNull(template, "template");
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (int index = 0; index < template.length();) {
            if (template.charAt(index) == '{' && index + 1 < template.length()
                    && template.charAt(index + 1) == '{') {
                index += 2;
                continue;
            }
            if (template.charAt(index) == '{') {
                int closing = template.indexOf('}', index + 1);
                if (closing > index + 1) {
                    String name = template.substring(index + 1, closing);
                    if (PLACEHOLDER_NAME.matcher(name).matches()) {
                        names.add(name);
                        index = closing + 1;
                        continue;
                    }
                }
            }
            index++;
        }
        return Collections.unmodifiableSet(names);
    }

    /**
     * Validates placeholder names and values before rendering.
     *
     * @param values placeholder map
     * @throws IllegalArgumentException if a name is invalid
     * @throws NullPointerException if a name or value is null
     */
    public static void validate(Map<String, ?> values) {
        Objects.requireNonNull(values, "values").forEach((name, value) -> {
            Objects.requireNonNull(name, "placeholder name");
            if (!PLACEHOLDER_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid placeholder name: " + name);
            }
            Objects.requireNonNull(value, "placeholder value for " + name);
        });
    }

    /**
     * Renders a template into plain component text. Values are never interpreted as markup.
     * Missing values leave the original placeholder visible.
     *
     * @param template source text
     * @param values placeholder values
     * @return rendered component
     */
    public static Component render(String template, Map<String, ?> values) {
        Objects.requireNonNull(template, "template");
        validate(values);
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
                    if (PLACEHOLDER_NAME.matcher(name).matches() && values.containsKey(name)) {
                        result = appendLiteral(result, literal)
                                .append(Component.text(String.valueOf(values.get(name))));
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
}
