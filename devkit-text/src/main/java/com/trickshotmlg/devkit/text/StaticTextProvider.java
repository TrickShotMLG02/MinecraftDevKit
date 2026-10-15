package com.trickshotmlg.devkit.text;

import net.kyori.adventure.text.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** A provider for applications that do not load translation catalogs. */
public final class StaticTextProvider implements TextProvider {
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
        TextFormatter.validate(placeholders);

        String value;
        if (reference instanceof TextRef.Literal literal) {
            value = literal.value();
        } else if (reference instanceof TextRef.Translated translated) {
            value = translated.fallback().orElseGet(() -> missingValue(translated.key(), locale));
        } else {
            throw new IllegalArgumentException("Unsupported text reference type: " + reference.getClass().getName());
        }
        return TextFormatter.render(value, placeholders);
    }

    private String missingValue(String key, Locale locale) {
        return switch (missingTranslationPolicy) {
            case SHOW_KEY -> key;
            case SHOW_MARKER -> "<missing:" + key + ">";
            case ERROR -> throw new MissingTranslationException(key, locale);
        };
    }

}
