package com.trickshotmlg.devkit.localization;

import com.trickshotmlg.devkit.text.TextCatalog;
import com.trickshotmlg.devkit.text.TextFormatter;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Compares an explicit reference catalog with locale catalogs without filesystem or server dependencies. */
public final class LocalizationScanner {
    private LocalizationScanner() { }
    /** Scans references and includes resource parsing diagnostics from the loader. */
    /**
     * Scans references and includes resource parsing diagnostics from the loader.
     * @param references declared translation references
     * @param loaded loaded catalogs and resource findings
     * @param options locale and strictness policy
     * @return immutable diagnostic report
     */
    public static LocalizationReport scan(TextCatalog references, TranslationResourceLoader.Result loaded,
                                          LocalizationScanOptions options) {
        LocalizationReport scanned = scan(references, loaded.catalogs(), options);
        ArrayList<LocalizationFinding> combined = new ArrayList<>(scanned.findings());
        for (LocalizationFinding finding : loaded.findings()) {
            LocalizationFinding.Severity severity = options.failOn().contains(finding.category())
                    ? LocalizationFinding.Severity.ERROR
                    : finding.category() == LocalizationFinding.Category.UNUSED_TRANSLATION
                        ? LocalizationFinding.Severity.INFO : LocalizationFinding.Severity.WARNING;
            combined.add(new LocalizationFinding(severity, finding.category(), finding.locale(), finding.key(),
                    finding.message(), finding.fallbackAvailable()));
        }
        boolean fails = combined.stream().anyMatch(finding -> options.failOn().contains(finding.category()));
        return new LocalizationReport(combined, fails);
    }
    /**
     * Scans an in-memory set of catalogs.
     * @param references declared text references
     * @param translations translation catalogs keyed by their locale
     * @param options scan policy
     * @return immutable diagnostic report
     */
    public static LocalizationReport scan(TextCatalog references, Map<Locale, TranslationCatalog> translations,
                                          LocalizationScanOptions options) {
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(translations, "translations");
        Objects.requireNonNull(options, "options");
        ArrayList<LocalizationFinding> findings = new ArrayList<>();
        translations.forEach((locale, catalog) -> {
            Objects.requireNonNull(locale, "translation locale");
            Objects.requireNonNull(catalog, "translation catalog");
            if (!locale.equals(catalog.locale())) throw new IllegalArgumentException("Translation map key "
                    + locale.toLanguageTag() + " does not match catalog locale " + catalog.locale().toLanguageTag());
        });
        for (TextCatalog.DeclarationConflict conflict : references.conflicts()) {
            add(findings, options, LocalizationFinding.Category.DUPLICATE_REFERENCE,
                    OptionalLocale.none(), conflict.key(), "Conflicting fallback declarations at " + conflict.source(), false);
        }
        Set<Locale> locales = new TreeSet<>(java.util.Comparator.comparing(Locale::toLanguageTag));
        locales.addAll(options.requiredLocales());
        locales.add(options.baseLocale());
        locales.addAll(translations.keySet());
        for (Locale required : options.requiredLocales()) {
            if (!translations.containsKey(required)) add(findings, options, LocalizationFinding.Category.INVALID_RESOURCE,
                    OptionalLocale.of(required), "", "Required locale catalog is missing", false);
        }
        TranslationCatalog base = translations.get(options.baseLocale());
        for (TextCatalog.Entry ref : references.entries().values()) {
            if (options.ignoredKeys().contains(ref.key())) continue;
            String baseline = base == null ? null : base.translations().get(ref.key());
            if (baseline == null) baseline = ref.fallback().orElseGet(() -> locales.stream()
                    .map(translations::get)
                    .filter(Objects::nonNull)
                    .map(catalog -> catalog.translations().get(ref.key()))
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null));
            Set<String> canonical = baseline == null ? Set.of() : TextFormatter.placeholders(baseline);
            for (Locale locale : locales) {
                TranslationCatalog catalog = translations.get(locale);
                String value = catalog == null ? null : catalog.translations().get(ref.key());
                if (value == null) {
                    boolean fallback = ref.fallback().isPresent();
                    LocalizationFinding.Category category = fallback
                            ? LocalizationFinding.Category.MISSING_TRANSLATION : LocalizationFinding.Category.MISSING_FALLBACK;
                    String message = catalog == null ? "Locale catalog is missing" : fallback
                            ? "Missing translation; fallback available" : "Missing translation and no fallback";
                    add(findings, options, category, OptionalLocale.of(locale), ref.key(), message, fallback);
                    if (options.requiredLocales().contains(locale) && base != null
                            && base.translations().containsKey(ref.key()) && !locale.equals(options.baseLocale())) {
                        add(findings, options, LocalizationFinding.Category.MISSING_LOCALE_COVERAGE,
                                OptionalLocale.of(locale), ref.key(), "Base locale translation is not covered", fallback);
                    }
                } else if (baseline != null && !TextFormatter.placeholders(value).equals(canonical)) {
                    add(findings, options, LocalizationFinding.Category.PLACEHOLDER_MISMATCH,
                            OptionalLocale.of(locale), ref.key(), "Placeholder mismatch: expected " + canonical
                                    + " but found " + TextFormatter.placeholders(value), ref.fallback().isPresent());
                }
            }
        }
        for (Map.Entry<Locale, TranslationCatalog> entry : translations.entrySet()) {
            for (String key : entry.getValue().translations().keySet()) {
                if (!references.entries().containsKey(key) && !options.ignoredKeys().contains(key)) {
                    add(findings, options, LocalizationFinding.Category.UNUSED_TRANSLATION,
                            OptionalLocale.of(entry.getKey()), key, "Translation key is not referenced", false);
                }
            }
        }
        boolean fails = findings.stream().anyMatch(f -> options.failOn().contains(f.category()));
        return new LocalizationReport(findings, fails);
    }
    private static void add(ArrayList<LocalizationFinding> findings, LocalizationScanOptions options,
                            LocalizationFinding.Category category, OptionalLocale locale,
                            String key, String message, boolean fallback) {
        LocalizationFinding.Severity severity = options.failOn().contains(category)
                ? LocalizationFinding.Severity.ERROR
                : category == LocalizationFinding.Category.UNUSED_TRANSLATION
                    ? LocalizationFinding.Severity.INFO : LocalizationFinding.Severity.WARNING;
        findings.add(new LocalizationFinding(severity, category, locale.value, key, message, fallback));
    }
    private static final class OptionalLocale {
        private final java.util.Optional<String> value;
        private OptionalLocale(java.util.Optional<String> value) { this.value = value; }
        static OptionalLocale none() { return new OptionalLocale(java.util.Optional.empty()); }
        static OptionalLocale of(Locale locale) { return new OptionalLocale(java.util.Optional.of(locale.toLanguageTag())); }
    }
}
