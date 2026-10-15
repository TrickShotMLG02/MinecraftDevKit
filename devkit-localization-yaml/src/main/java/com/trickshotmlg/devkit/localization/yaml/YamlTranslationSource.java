package com.trickshotmlg.devkit.localization.yaml;

import com.trickshotmlg.devkit.localization.TranslationCatalog;
import com.trickshotmlg.devkit.localization.TranslationSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** YAML-backed translation source for bundled classpath resources or an external directory. */
public final class YamlTranslationSource implements TranslationSource {
    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9_-]+\\.yml");
    private final Loader loader;
    private final String description;

    private YamlTranslationSource(Loader loader, String description) {
        this.loader = loader;
        this.description = description;
    }

    /** Creates an external source. Files may be {@code <tag>.yml} or {@code <tag>/*.yml}. */
    public static YamlTranslationSource directory(Path directory) {
        Objects.requireNonNull(directory, "directory");
        return new YamlTranslationSource(() -> loadDirectory(directory), "external:" + directory);
    }

    /**
     * Creates a bundled source using {@code <tag>.yml}, or an explicit {@code <tag>/index.txt}
     * resource listing YAML file names relative to {@code <tag>/}. The index is UTF-8, one file
     * name per line. Explicit manifests make resource discovery work inside JAR files.
     */
    public static YamlTranslationSource bundled(String resourceDirectory, String... localeTags) {
        return bundled(YamlTranslationSource.class.getClassLoader(), resourceDirectory, localeTags);
    }

    /** Creates a bundled source using the supplied class loader. */
    public static YamlTranslationSource bundled(ClassLoader loader, String resourceDirectory,
                                                 String... localeTags) {
        Objects.requireNonNull(loader, "loader");
        String root = normalizeResourceDirectory(resourceDirectory);
        List<Locale> locales = normalizeLocales(List.of(localeTags));
        return new YamlTranslationSource(() -> {
            TreeMap<Locale, TranslationCatalog> result = localeMap();
            for (Locale locale : locales) {
                String tag = locale.toLanguageTag();
                String localeDirectory = join(root, tag);
                List<String> names = readIndex(loader, join(localeDirectory, "index.txt"));
                if (names == null) names = List.of(tag + ".yml");
                Map<String, String> combined = new TreeMap<>();
                for (String name : names) {
                    String resource = name.equals(tag + ".yml") ? join(root, name) : join(localeDirectory, name);
                    try (InputStream input = loader.getResourceAsStream(resource)) {
                        if (input == null) throw new IOException("Missing bundled translation resource: " + resource);
                        merge(combined, YamlCatalogParser.parse(input, resource), resource);
                    }
                }
                result.put(locale, TranslationCatalog.of(locale, combined));
            }
            return Collections.unmodifiableMap(result);
        }, "bundled:" + root);
    }

    @Override
    public Map<Locale, TranslationCatalog> load() throws IOException {
        return loader.load();
    }

    @Override
    public String description() { return description; }

    private static Map<Locale, TranslationCatalog> loadDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return Map.of();
        if (!Files.isDirectory(directory)) throw new IOException("Translation path is not a directory: " + directory);
        TreeMap<Locale, Map<String, String>> values = new TreeMap<>(Comparator.comparing(Locale::toLanguageTag));
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .sorted(Comparator.comparing(path -> directory.relativize(path).toString()))
                    .toList()) {
                Path relative = directory.relativize(file);
                String fileName = file.getFileName().toString();
                String localeTag = relative.getNameCount() == 1
                        ? fileName.substring(0, fileName.length() - 4)
                        : relative.getName(0).toString();
                Locale locale = parseLocale(localeTag, file.toString());
                Map<String, String> target = values.computeIfAbsent(locale, ignored -> new TreeMap<>());
                try (InputStream input = Files.newInputStream(file)) {
                    merge(target, YamlCatalogParser.parse(input, file.toString()), file.toString());
                }
            }
        }
        TreeMap<Locale, TranslationCatalog> result = localeMap();
        values.forEach((locale, entries) -> result.put(locale, TranslationCatalog.of(locale, entries)));
        return Collections.unmodifiableMap(result);
    }

    private static List<String> readIndex(ClassLoader loader, String resource) throws IOException {
        try (InputStream input = loader.getResourceAsStream(resource)) {
            if (input == null) return null;
            List<String> names = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
            if (names.isEmpty()) throw new IOException("Bundled translation index is empty: " + resource);
            for (String name : names) {
                if (!FILE_NAME.matcher(name).matches() || name.equals("index.txt")) {
                    throw new IOException("Invalid YAML filename in " + resource + ": " + name);
                }
            }
            return names;
        }
    }

    private static List<Locale> normalizeLocales(Collection<String> tags) {
        Objects.requireNonNull(tags, "localeTags");
        ArrayList<Locale> result = new ArrayList<>();
        for (String tag : tags) {
            Locale locale = parseLocale(Objects.requireNonNull(tag, "locale tag"), "configured bundled locales");
            if (result.contains(locale)) throw new IllegalArgumentException("Duplicate bundled locale: " + tag);
            result.add(locale);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("At least one bundled locale is required");
        return List.copyOf(result);
    }

    private static Locale parseLocale(String tag, String source) {
        try {
            Locale locale = new Locale.Builder().setLanguageTag(tag).build();
            if (locale.getLanguage().isEmpty() || !locale.toLanguageTag().equalsIgnoreCase(tag)) {
                throw new IllegalArgumentException("Locale must be a valid BCP 47 language tag");
            }
            return locale;
        } catch (java.util.IllformedLocaleException exception) {
            throw new IllegalArgumentException("Invalid locale identifier '" + tag + "' in " + source, exception);
        }
    }

    private static void merge(Map<String, String> destination, Map<String, String> additions, String source) throws IOException {
        for (Map.Entry<String, String> entry : additions.entrySet()) {
            if (destination.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                throw new IOException("Duplicate translation key '" + entry.getKey() + "' at the same source priority (" + source + ")");
            }
        }
    }

    private static TreeMap<Locale, TranslationCatalog> localeMap() {
        return new TreeMap<>(Comparator.comparing(Locale::toLanguageTag));
    }

    private static String normalizeResourceDirectory(String directory) {
        Objects.requireNonNull(directory, "resourceDirectory");
        String normalized = directory.replace('\\', '/').replaceAll("^/+|/+$", "");
        if (normalized.contains("..")) throw new IllegalArgumentException("Resource directory may not contain '..'");
        return normalized;
    }

    private static String join(String parent, String child) {
        return parent.isEmpty() ? child : parent + "/" + child;
    }

    @FunctionalInterface
    private interface Loader { Map<Locale, TranslationCatalog> load() throws IOException; }
}
