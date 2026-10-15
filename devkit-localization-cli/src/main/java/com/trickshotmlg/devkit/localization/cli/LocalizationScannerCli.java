package com.trickshotmlg.devkit.localization.cli;

import com.trickshotmlg.devkit.localization.LocalizationFinding;
import com.trickshotmlg.devkit.localization.LocalizationReport;
import com.trickshotmlg.devkit.localization.LocalizationReportJson;
import com.trickshotmlg.devkit.localization.LocalizationScanOptions;
import com.trickshotmlg.devkit.localization.LocalizationScanner;
import com.trickshotmlg.devkit.localization.TextCatalogManifest;
import com.trickshotmlg.devkit.localization.TranslationResourceLoader;
import com.trickshotmlg.devkit.localization.TranslationSnapshot;
import com.trickshotmlg.devkit.localization.TranslationSource;
import com.trickshotmlg.devkit.localization.yaml.YamlTranslationSource;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Command-line entry point for deterministic localization diagnostics. */
public final class LocalizationScannerCli {
    private LocalizationScannerCli() { }
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    public static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            if (args.length == 0 || Arrays.asList(args).contains("--help") || Arrays.asList(args).contains("-h")) {
                out.print(HELP); return 0;
            }
            Path translations = null, references = null, output = null;
            String baseTag = "en-US", fallbackTag = null, format = "text", resourceFormat = "auto";
            String failOn = "ERROR", localeTags = "";
            boolean defaultFallbackEnabled = true;
            for (int i = 0; i < args.length; i++) {
                String flag = args[i];
                if (flag.equals("--disable-default-fallback")) { defaultFallbackEnabled = false; continue; }
                if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value for " + flag);
                String value = args[++i];
                switch (flag) {
                    case "--translations" -> translations = Path.of(value);
                    case "--references" -> references = Path.of(value);
                    case "--base-locale" -> baseTag = value;
                    case "--locales" -> localeTags = value;
                    case "--format" -> format = value;
                    case "--resource-format" -> resourceFormat = value;
                    case "--fallback-locale" -> fallbackTag = value;
                    case "--fail-on" -> failOn = value;
                    case "--output" -> output = Path.of(value);
                    default -> throw new IllegalArgumentException("Unknown option: " + flag);
                }
            }
            if (translations == null || references == null) throw new IllegalArgumentException("--translations and --references are required");
            if (!format.equals("text") && !format.equals("json")) throw new IllegalArgumentException("--format must be text or json");
            if (!Set.of("auto", "properties", "yaml").contains(resourceFormat)) {
                throw new IllegalArgumentException("--resource-format must be auto, properties, or yaml");
            }
            Locale base = locale(baseTag);
            Locale fallback = fallbackTag == null ? base : locale(fallbackTag);
            Set<Locale> locales = new TreeSet<>(java.util.Comparator.comparing(Locale::toLanguageTag));
            locales.add(base);
            if (!localeTags.isBlank()) for (String tag : localeTags.split(",")) locales.add(locale(tag.trim()));
            Set<LocalizationFinding.Category> strict = strictCategories(failOn);
            var options = new LocalizationScanOptions(base, locales, strict, Set.of());
            var catalog = TextCatalogManifest.read(references);
            List<TranslationSource> sources = new ArrayList<>();
            List<com.trickshotmlg.devkit.localization.LocalizationFinding> sourceFindings = new ArrayList<>();
            boolean loadProperties = resourceFormat.equals("properties")
                    || resourceFormat.equals("auto") && containsExtension(translations, ".properties");
            boolean loadYaml = resourceFormat.equals("yaml")
                    || resourceFormat.equals("auto") && containsExtension(translations, ".yml");
            if (loadProperties) {
                var loaded = TranslationResourceLoader.load(translations);
                Path propertiesDirectory = translations;
                sources.add(new TranslationSource() {
                    @Override public Map<Locale, com.trickshotmlg.devkit.localization.TranslationCatalog> load() {
                        return loaded.catalogs();
                    }
                    @Override public String description() { return "properties:" + propertiesDirectory; }
                });
                sourceFindings.addAll(loaded.findings());
            }
            if (loadYaml) sources.add(YamlTranslationSource.directory(translations));
            TranslationSnapshot snapshot;
            try {
                snapshot = TranslationSnapshot.load(sources);
            } catch (Exception exception) {
                sourceFindings.add(new com.trickshotmlg.devkit.localization.LocalizationFinding(
                        com.trickshotmlg.devkit.localization.LocalizationFinding.Severity.ERROR,
                        com.trickshotmlg.devkit.localization.LocalizationFinding.Category.INVALID_RESOURCE,
                        java.util.Optional.empty(), translations.toString(), exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage(), false));
                snapshot = TranslationSnapshot.bundled(Map.of());
            }
            LocalizationReport report = LocalizationScanner.scan(catalog, snapshot, options, fallback,
                    defaultFallbackEnabled, sourceFindings);
            String rendered = format.equals("json") ? LocalizationReportJson.write(report) : renderText(report);
            if (output == null) out.println(rendered);
            else {
                Path parent = output.toAbsolutePath().getParent(); if (parent != null) Files.createDirectories(parent);
                Files.writeString(output, rendered + System.lineSeparator(), StandardCharsets.UTF_8);
            }
            return report.failsBuild() ? 1 : 0;
        } catch (Exception exception) {
            err.println("Localization scan failed: " + exception.getMessage());
            return 2;
        }
    }
    private static Set<LocalizationFinding.Category> strictCategories(String input) {
        if (input.equalsIgnoreCase("none")) return Set.of();
        if (input.equalsIgnoreCase("ERROR")) return EnumSet.of(LocalizationFinding.Category.MISSING_FALLBACK,
                LocalizationFinding.Category.PLACEHOLDER_MISMATCH, LocalizationFinding.Category.INVALID_RESOURCE,
                LocalizationFinding.Category.DUPLICATE_REFERENCE);
        if (input.equalsIgnoreCase("WARNING")) return EnumSet.complementOf(EnumSet.of(LocalizationFinding.Category.UNUSED_TRANSLATION));
        EnumSet<LocalizationFinding.Category> result = EnumSet.noneOf(LocalizationFinding.Category.class);
        for (String name : input.split(",")) result.add(LocalizationFinding.Category.valueOf(name.trim().toUpperCase(Locale.ROOT)));
        return result;
    }
    private static Locale locale(String tag) {
        Locale result = new Locale.Builder().setLanguageTag(tag).build();
        if (result.getLanguage().isEmpty()) throw new IllegalArgumentException("Invalid locale: " + tag);
        return result;
    }
    private static boolean containsExtension(Path directory, String extension) throws IOException {
        if (!Files.isDirectory(directory)) return false;
        try (var paths = Files.walk(directory)) {
            return paths.anyMatch(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension));
        }
    }
    private static String renderText(LocalizationReport report) {
        StringBuilder text = new StringBuilder();
        for (LocalizationFinding finding : report.findings()) {
            text.append(switch (finding.severity()) { case ERROR -> "ERROR"; case WARNING -> "WARN "; case INFO -> "INFO "; })
                    .append("  [").append(finding.locale().orElse("-")).append("] ").append(finding.key()).append(System.lineSeparator())
                    .append("       ").append(finding.message()).append(System.lineSeparator());
        }
        text.append("Counts: ").append(report.counts());
        return text.toString();
    }
    private static final String HELP = "MinecraftDevKit localization scanner\n"
            + "Usage: localization-scanner --translations <directory> --references <manifest.json> [options]\n"
            + "  --base-locale <tag>   Base locale (default en-US)\n"
            + "  --locales <tags>      Required comma-separated locale tags\n"
            + "  --fallback-locale <tag>  Global fallback locale (defaults to base locale)\n"
            + "  --disable-default-fallback  Disable global fallback lookup\n"
            + "  --resource-format auto|properties|yaml  Translation format (default auto)\n"
            + "  --format text|json    Output format (default text)\n"
            + "  --fail-on <severity|category,...>  ERROR, WARNING, none, or finding categories\n"
            + "  --output <file>       Write report to a file\n"
            + "Exit codes: 0 clean, 1 strict findings, 2 invalid invocation/input.\n";
}
