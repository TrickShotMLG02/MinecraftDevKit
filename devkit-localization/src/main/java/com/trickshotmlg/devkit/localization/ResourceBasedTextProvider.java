package com.trickshotmlg.devkit.localization;

import com.trickshotmlg.devkit.text.MissingTranslationException;
import com.trickshotmlg.devkit.text.MissingTranslationPolicy;
import com.trickshotmlg.devkit.text.TextFormatter;
import com.trickshotmlg.devkit.text.TextProvider;
import com.trickshotmlg.devkit.text.TextRef;
import net.kyori.adventure.text.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Resolves references from immutable locale catalogs with deterministic fallback behavior. */
public final class ResourceBasedTextProvider implements TextProvider {
    private final Map<Locale, TranslationCatalog> catalogs;
    private final Locale defaultLocale;
    private final MissingTranslationPolicy missingTranslationPolicy;
    private final TextDiagnosticSink diagnosticSink;
    private final Set<DiagnosticIdentity> reported = ConcurrentHashMap.newKeySet();

    private ResourceBasedTextProvider(Collection<TranslationCatalog> catalogs, Locale defaultLocale,
                                      MissingTranslationPolicy missingTranslationPolicy,
                                      TextDiagnosticSink diagnosticSink) {
        Objects.requireNonNull(catalogs, "catalogs");
        Map<Locale, TranslationCatalog> sorted = new java.util.TreeMap<>(Comparator.comparing(Locale::toLanguageTag));
        for (TranslationCatalog catalog : catalogs) {
            Objects.requireNonNull(catalog, "catalog");
            Locale normalizedLocale = LocaleFallbackResolver.normalize(catalog.locale());
            TranslationCatalog normalizedCatalog = TranslationCatalog.of(normalizedLocale, catalog.translations());
            if (sorted.putIfAbsent(normalizedLocale, normalizedCatalog) != null) {
                throw new IllegalArgumentException("Duplicate translation catalog for locale " + normalizedLocale);
            }
        }
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("At least one translation catalog is required");
        }
        this.defaultLocale = requireLocale(defaultLocale, "defaultLocale");
        this.catalogs = Collections.unmodifiableMap(sorted);
        this.missingTranslationPolicy = Objects.requireNonNull(missingTranslationPolicy, "missingTranslationPolicy");
        this.diagnosticSink = Objects.requireNonNull(diagnosticSink, "diagnosticSink");
    }

    /**
     * Creates a provider from already parsed immutable catalogs.
     *
     * @param catalogs loaded catalogs
     * @param defaultLocale fallback locale
     * @param policy behavior when no catalog or reference fallback contains a key
     * @param diagnostics structured diagnostic sink
     * @return configured provider
     */
    public static ResourceBasedTextProvider of(Collection<TranslationCatalog> catalogs, Locale defaultLocale,
                                               MissingTranslationPolicy policy, TextDiagnosticSink diagnostics) {
        return new ResourceBasedTextProvider(catalogs, defaultLocale, policy, diagnostics);
    }

    /**
     * Loads UTF-8 {@code <locale-tag>.properties} files from a directory.
     *
     * @param directory directory containing catalogs
     * @param defaultLocale fallback locale
     * @param policy behavior when no catalog or reference fallback contains a key
     * @param diagnostics structured diagnostic sink
     * @return configured provider
     */
    public static ResourceBasedTextProvider fromDirectory(Path directory, Locale defaultLocale,
                                                          MissingTranslationPolicy policy,
                                                          TextDiagnosticSink diagnostics) {
        Objects.requireNonNull(directory, "directory");
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Translation directory does not exist or is not a directory: " + directory);
        }
        try (Stream<Path> paths = Files.list(directory)) {
            ArrayList<TranslationCatalog> catalogs = new ArrayList<>();
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> catalogs.add(loadFile(path)));
            return of(catalogs, defaultLocale, policy, diagnostics);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read translation directory " + directory, exception);
        }
    }

    /**
     * Loads UTF-8 {@code <locale-tag>.properties} resources beneath a classpath directory.
     *
     * @param loader class loader containing the resources
     * @param resourceDirectory directory path such as {@code translations}
     * @param locales locales to load; each must have a matching resource
     * @param defaultLocale fallback locale
     * @param policy behavior when no catalog or reference fallback contains a key
     * @param diagnostics structured diagnostic sink
     * @return configured provider
     */
    public static ResourceBasedTextProvider fromClasspath(ClassLoader loader, String resourceDirectory,
                                                           Collection<Locale> locales, Locale defaultLocale,
                                                           MissingTranslationPolicy policy,
                                                           TextDiagnosticSink diagnostics) {
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(resourceDirectory, "resourceDirectory");
        Objects.requireNonNull(locales, "locales");
        String directory = resourceDirectory.replace('\\', '/').replaceAll("^/+|/+$", "");
        ArrayList<TranslationCatalog> catalogs = new ArrayList<>();
        for (Locale locale : locales.stream().map(value -> requireLocale(value, "locale"))
                .sorted(Comparator.comparing(Locale::toLanguageTag)).toList()) {
            String fileName = locale.toLanguageTag() + ".properties";
            String resource = directory.isEmpty() ? fileName : directory + "/" + fileName;
            try (InputStream stream = loader.getResourceAsStream(resource)) {
                if (stream == null) {
                    throw new IllegalArgumentException("Missing translation resource: " + resource);
                }
                catalogs.add(load(locale, new InputStreamReader(stream, StandardCharsets.UTF_8), resource));
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed to load translation resource " + resource, exception);
            }
        }
        return of(catalogs, defaultLocale, policy, diagnostics);
    }

    /**
     * Returns the configured fallback locale.
     *
     * @return default locale
     */
    public Locale defaultLocale() {
        return defaultLocale;
    }

    /**
     * Returns catalogs in locale-tag order.
     *
     * @return immutable locale catalog map
     */
    public Map<Locale, TranslationCatalog> catalogs() {
        return catalogs;
    }

    @Override
    public Component resolve(TextRef reference, Locale locale, Map<String, ?> placeholders) {
        return resolveWithTrace(reference, locale, placeholders, Optional.empty()).component();
    }

    @Override
    public Component resolve(TextRef reference, Locale locale, Map<String, ?> placeholders,
                             Optional<String> sourceContext) {
        return resolveWithTrace(reference, locale, placeholders, sourceContext).component();
    }

    /** Resolves a reference and returns the locale/fallback provenance for diagnostics or tooling. */
    public TextResolution resolveWithTrace(TextRef reference, Locale locale, Map<String, ?> placeholders) {
        return resolveWithTrace(reference, locale, placeholders, Optional.empty());
    }

    /** Resolves with provenance while associating diagnostics with a stable source identifier. */
    public TextResolution resolveWithTrace(TextRef reference, Locale locale, Map<String, ?> placeholders,
                                           Optional<String> sourceContext) {
        Objects.requireNonNull(reference, "reference");
        Locale requested = LocaleFallbackResolver.normalize(locale);
        Objects.requireNonNull(sourceContext, "sourceContext");
        TextFormatter.validate(placeholders);

        String template;
        TextResolution.Fallback fallback;
        Optional<Locale> resolvedLocale;
        Optional<String> key = reference.translationKey();
        boolean referenceFallbackAvailable = reference.fallback().isPresent();
        if (reference instanceof TextRef.Literal literal) {
            template = literal.value();
            fallback = TextResolution.Fallback.NONE;
            resolvedLocale = Optional.empty();
        } else {
            String translationKey = key.orElseThrow();
            Lookup lookup = findTranslation(translationKey, requested);
            if (lookup != null) {
                template = lookup.value();
                fallback = lookup.fallback();
                resolvedLocale = Optional.of(lookup.locale());
                if (fallback != TextResolution.Fallback.NONE) {
                    TextDiagnostic.Outcome outcome = lookup.outcome();
                    report(new TextDiagnostic(TextDiagnostic.Kind.MISSING_TRANSLATION, translationKey, requested,
                            referenceFallbackAvailable, outcome, Optional.empty(), sourceContext));
                }
            } else if (reference.fallback().isPresent()) {
                template = reference.fallback().orElseThrow();
                fallback = TextResolution.Fallback.TEXT_REF;
                resolvedLocale = Optional.empty();
                report(new TextDiagnostic(TextDiagnostic.Kind.MISSING_TRANSLATION, translationKey, requested,
                        true, TextDiagnostic.Outcome.REFERENCE_FALLBACK, Optional.empty(), sourceContext));
            } else {
                TextDiagnostic.Outcome outcome = switch (missingTranslationPolicy) {
                    case SHOW_KEY -> TextDiagnostic.Outcome.SHOW_KEY;
                    case SHOW_MARKER -> TextDiagnostic.Outcome.SHOW_MARKER;
                    case ERROR -> TextDiagnostic.Outcome.ERROR;
                };
                report(new TextDiagnostic(TextDiagnostic.Kind.MISSING_TRANSLATION, translationKey, requested,
                        false, outcome, Optional.empty(), sourceContext));
                if (missingTranslationPolicy == MissingTranslationPolicy.ERROR) {
                    throw new MissingTranslationException(translationKey, requested);
                }
                template = missingTranslationPolicy == MissingTranslationPolicy.SHOW_KEY
                        ? translationKey : "<missing:" + translationKey + ">";
                fallback = TextResolution.Fallback.MISSING_KEY_POLICY;
                resolvedLocale = Optional.empty();
            }
        }

        if (key.isPresent()) {
            for (String placeholder : TextFormatter.placeholders(template)) {
                if (!placeholders.containsKey(placeholder)) {
                    report(new TextDiagnostic(TextDiagnostic.Kind.MISSING_PLACEHOLDER, key.orElseThrow(), requested,
                            referenceFallbackAvailable, TextDiagnostic.Outcome.PLACEHOLDER_UNRESOLVED,
                            Optional.of(placeholder), sourceContext));
                }
            }
        }
        return new TextResolution(TextFormatter.render(template, placeholders), requested, resolvedLocale,
                fallback, resolvedLocale.map(ignored -> "resource-catalog"));
    }

    private Lookup findTranslation(String key, Locale requested) {
        for (Locale candidate : LocaleFallbackResolver.chain(requested, defaultLocale, true)) {
            TranslationCatalog catalog = catalogs.get(candidate);
            if (catalog != null && catalog.translations().containsKey(key)) {
                boolean isRequestedParent = LocaleFallbackResolver.parents(requested).contains(candidate)
                        && !candidate.equals(requested);
                if (candidate.equals(requested)) {
                    return new Lookup(candidate, catalog.translations().get(key), null,
                            TextResolution.Fallback.NONE);
                }
                if (isRequestedParent) {
                    return new Lookup(candidate, catalog.translations().get(key),
                            TextDiagnostic.Outcome.PARENT_LOCALE_FALLBACK, TextResolution.Fallback.PARENT_LOCALE);
                }
                return new Lookup(candidate, catalog.translations().get(key),
                        TextDiagnostic.Outcome.DEFAULT_LOCALE_FALLBACK, TextResolution.Fallback.DEFAULT_LOCALE);
            }
        }
        return null;
    }

    private void report(TextDiagnostic diagnostic) {
        DiagnosticIdentity identity = new DiagnosticIdentity(diagnostic.kind(), diagnostic.translationKey(),
                diagnostic.requestedLocale(), diagnostic.placeholder());
        if (reported.add(identity)) {
            diagnosticSink.report(diagnostic);
        }
    }

    private static Locale requireLocale(Locale locale, String name) {
        try {
            return LocaleFallbackResolver.normalize(locale);
        } catch (NullPointerException exception) {
            throw new NullPointerException(name);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " must have a language tag", exception);
        }
    }

    private static TranslationCatalog loadFile(Path path) {
        String fileName = path.getFileName().toString();
        String tag = fileName.substring(0, fileName.length() - ".properties".length());
        Locale locale;
        try {
            locale = new Locale.Builder().setLanguageTag(tag).build();
        } catch (java.util.IllformedLocaleException exception) {
            throw new IllegalArgumentException("Invalid locale tag in translation filename '" + fileName + "'", exception);
        }
        try {
            return load(locale, Files.newBufferedReader(path, StandardCharsets.UTF_8), path.toString());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load translation file " + path, exception);
        }
    }

    private static TranslationCatalog load(Locale locale, java.io.Reader reader, String source) {
        Properties properties = new Properties();
        try (reader) {
            properties.load(reader);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load translation resource " + source, exception);
        }
        Map<String, String> entries = new HashMap<>();
        properties.stringPropertyNames().forEach(key -> entries.put(key, properties.getProperty(key)));
        try {
            return TranslationCatalog.of(locale, entries);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid translation entry in " + source + ": " + exception.getMessage(), exception);
        }
    }

    private record Lookup(Locale locale, String value, TextDiagnostic.Outcome outcome,
                          TextResolution.Fallback fallback) {
    }

    private record DiagnosticIdentity(TextDiagnostic.Kind kind, String key, Locale locale,
                                      Optional<String> placeholder) {
    }
}
