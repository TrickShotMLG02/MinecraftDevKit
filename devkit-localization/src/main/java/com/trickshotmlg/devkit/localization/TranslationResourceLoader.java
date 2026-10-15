package com.trickshotmlg.devkit.localization;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/** Loads deterministic UTF-8 {@code <locale-tag>.properties} translation catalogs and resource findings. */
public final class TranslationResourceLoader {
    private TranslationResourceLoader() { }
    public static Result load(Path directory) {
        ArrayList<LocalizationFinding> findings = new ArrayList<>();
        TreeMap<Locale, TranslationCatalog> catalogs = new TreeMap<>(java.util.Comparator.comparing(Locale::toLanguageTag));
        if (!Files.isDirectory(directory)) {
            findings.add(new LocalizationFinding(LocalizationFinding.Severity.ERROR,
                    LocalizationFinding.Category.INVALID_RESOURCE, java.util.Optional.empty(), directory.toString(),
                    "Translation directory does not exist or is not a directory", false));
            return new Result(catalogs, findings);
        }
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".properties"))
                    .sorted().toList()) {
                String file = path.getFileName().toString();
                String tag = file.substring(0, file.length() - ".properties".length());
                Locale locale;
                try {
                    locale = new Locale.Builder().setLanguageTag(tag).build();
                    if (locale.getLanguage().isEmpty() || !locale.toLanguageTag().equalsIgnoreCase(tag))
                        throw new java.util.IllformedLocaleException("Locale tag must be a canonical BCP 47 language tag");
                } catch (java.util.IllformedLocaleException exception) {
                    findings.add(finding(tag, file, "Invalid locale identifier: " + exception.getMessage()));
                    continue;
                }
                if (catalogs.containsKey(locale)) {
                    findings.add(finding(tag, file, "Duplicate locale catalog")); continue;
                }
                TrackingProperties properties = new TrackingProperties();
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { properties.load(reader); }
                catch (IOException | IllegalArgumentException exception) {
                    findings.add(finding(tag, file, "Malformed translation resource: " + exception.getMessage()));
                    continue;
                }
                for (String duplicate : properties.duplicates) findings.add(new LocalizationFinding(
                        LocalizationFinding.Severity.WARNING, LocalizationFinding.Category.INVALID_RESOURCE,
                        java.util.Optional.of(tag), duplicate, "Duplicate translation key in " + file, false));
                TreeMap<String, String> values = new TreeMap<>();
                properties.stringPropertyNames().forEach(key -> values.put(key, properties.getProperty(key)));
                try { catalogs.put(locale, TranslationCatalog.of(locale, values)); }
                catch (RuntimeException exception) { findings.add(finding(tag, file, "Invalid translation entry: " + exception.getMessage())); }
            }
        } catch (IOException exception) { findings.add(finding("", directory.toString(), "Could not read translation directory: " + exception.getMessage())); }
        return new Result(catalogs, findings);
    }
    private static LocalizationFinding finding(String locale, String key, String message) {
        return new LocalizationFinding(LocalizationFinding.Severity.ERROR, LocalizationFinding.Category.INVALID_RESOURCE,
                locale.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(locale), key, message, false);
    }
    private static final class TrackingProperties extends Properties {
        private final java.util.Set<String> duplicates = new java.util.TreeSet<>();
        @Override public synchronized Object put(Object key, Object value) {
            if (containsKey(key)) duplicates.add(String.valueOf(key));
            return super.put(key, value);
        }
    }
    public record Result(Map<Locale, TranslationCatalog> catalogs, java.util.List<LocalizationFinding> findings) {
        public Result {
            TreeMap<Locale, TranslationCatalog> ordered = new TreeMap<>(java.util.Comparator.comparing(Locale::toLanguageTag));
            ordered.putAll(catalogs);
            catalogs = Collections.unmodifiableMap(ordered);
            findings = List.copyOf(findings);
        }
    }
}
