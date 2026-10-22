# devkit-localization

`devkit-localization` resolves existing `TextRef` values using immutable catalogs. It is independent of Bukkit and does not store player preferences or select a user's language. The caller supplies a `Locale` for each resolution. YAML parsing lives in the separate `devkit-localization-yaml` artifact.

## Reloadable service

```java
LocalizationService localization = LocalizationService.builder()
        .source(YamlTranslationSource.bundled("lang", "en", "en-GB", "de", "de-AT"))
        .source(YamlTranslationSource.directory(pluginDataFolder.resolve("lang")))
        .defaultFallbackLocale(Locale.forLanguageTag("en"))
        .defaultFallbackEnabled(true)
        .missingKeyPolicy(MissingTranslationPolicy.SHOW_MARKER)
        .diagnosticSink(diagnostic -> logger.warning(diagnostic.toString()))
        .build();

ReloadResult loaded = localization.reload();
if (!loaded.successful()) {
    throw new IllegalStateException(loaded.error().orElse("Localization load failed"));
}

Component message = localization.resolve(
        TextRef.translated("friends.add.success"),
        Locale.forLanguageTag("en-GB"),
        Map.of("player", "Steve")
);
```

`LocalizationService` itself implements the existing `TextProvider`. Keep and pass the same instance to command services; a successful reload changes the snapshot used by later messages without rebuilding commands. Before the first successful load it has an empty catalog and follows the configured missing-key behavior. `isLoaded()` and `generation()` expose initialization state.

The service accepts any `TranslationSource`. The YAML artifact provides `YamlTranslationSource.bundled(...)` and `.directory(...)`; the existing `.properties` provider remains available for immutable catalogs. Multiple service instances are independent.

## YAML files and source layering

External sources read UTF-8 `.yml` files in deterministic relative-path order. Supported layouts can be mixed:

```text
lang/en.yml
lang/en-GB.yml
lang/de.yml
lang/en/commands.yml
lang/en/friends.yml
lang/de/commands.yml
```

Keys are declared in YAML, not inferred from file names. A nested mapping:

```yaml
friends:
  add:
    success: "Added {player} as a friend."
```

declares `friends.add.success`. Root `<tag>.yml` and all files below `<tag>/` contribute to the same locale. Duplicate keys within one YAML document, after flattening, or across files at the same source layer are rejected. External values intentionally override bundled values at the same locale. See the [`devkit-localization-yaml` guide](../devkit-localization-yaml/README.md) for safe-parser limits and classpath resource indexing.

For bundled resources, `YamlTranslationSource.bundled("lang", "en", "de")` reads `lang/en.yml` and `lang/de.yml`. A JAR cannot reliably enumerate arbitrary classpath directories, so multiple bundled files for one locale use `lang/en/index.txt`, listing file names such as `commands.yml` and `friends.yml` one per line.

## Locale resolution

Locale tags are normalized using `Locale`/BCP 47. Extensions are dropped because they do not define separate translation catalogs. The parent resolver removes subtags from right to left: variant, region, then script. Examples:

```text
en-GB  -> en
de-AT  -> de
zh-Hant-TW -> zh-Hant -> zh
```

For each requested locale candidate, the service checks external translations before bundled translations. Locale specificity therefore wins over source priority. For example, bundled `en-GB` beats external `en`. The full lookup sequence is:

1. Requested locale and each parent, external then bundled at each locale.
2. If enabled, configured default fallback locale and its parents, again external then bundled.
3. Optional `TextRef` fallback.
4. `SHOW_KEY`, `SHOW_MARKER`, or `ERROR` missing-key policy.

Repeated locales in the chain are searched once. Set `.defaultFallbackEnabled(false)` to skip the configured global fallback. The fallback locale is optional; English is not hardcoded. A `TextRef` fallback remains independent of global fallback and never suppresses a missing-key diagnostic.

`resolveWithTrace(...)` returns a `TextResolution` with requested locale, resolved locale, source description, and whether resolution used a parent locale, configured default, reference fallback, or missing-key policy. Parent and default-locale fallback diagnostics are distinct.

## Formatting, diagnostics, and safety

Catalog templates support trusted MiniMessage formatting and `{name}` placeholders. For example:

```yaml
friends:
  add:
    success: "<green>Added {player} as a friend."
```

Dynamic values are inserted as plain Adventure components and are never parsed as MiniMessage. `TextRef.literal(...)` and `TextRef` fallback strings remain plain text. Missing placeholders stay visible and produce a structured `TextDiagnostic`. Repeated diagnostics are deduplicated by kind, key, locale, and placeholder for the lifetime of a service. Missing translations are still reported when a parent/default translation or a `TextRef` fallback supplies display text.

## Reload lifecycle and file watching

`reload()` loads and validates every source into a new immutable `TranslationSnapshot`; only then does one atomic reference publish it. If any source fails, the prior active snapshot remains available. `ReloadResult` reports success, whether content changed, generation, locales, entry count, source descriptions, and an error when loading failed.

`reloadAsync()` runs on a single owned daemon executor by default. The default executor allows one running reload and one queued reload; overload is reported through an exceptionally completed stage instead of an unbounded queue. Supply a caller-owned executor with `.reloadExecutor(executor)` if the application controls its own lifecycle; the service will not shut that executor down. Reload calls are serialized. Call `close()` during plugin shutdown.

External file watching is opt-in:

```java
localization.startWatching();
// optional: localization.startWatching(Duration.ofMillis(400));
localization.stopWatching();
```

The watcher creates and watches configured external YAML directories, watches locale subdirectories, and debounces YAML create/modify/delete events before invoking the same reload pipeline. It also reloads when a watched locale directory is removed, then cancels registrations beneath that directory. Bundled JAR resources are not watched. File-system watch notifications vary by OS and editor, so explicit reload remains the reliable fallback. `watcherFailureHandler(...)` receives watcher and automatic-reload failures. Close the service to stop the watcher and its owned executor.

## Existing `.properties` provider

`ResourceBasedTextProvider` remains available for UTF-8 `<tag>.properties` catalogs and now uses the same BCP 47 parent resolver. It is an immutable one-time-loaded provider. For new Bukkit plugin development with YAML and live updates, use `LocalizationService` with the YAML source module.

## Translation reference scanner

Build an explicit `TextCatalog` from command descriptions/usages, framework errors, and application-owned references. Generate a deterministic JSON manifest with `TextCatalogManifest`. Dynamic keys cannot be discovered reliably from arbitrary Java source and must be registered explicitly.

The scanner uses the same `TranslationSnapshot` and locale/source lookup rules as runtime. Partial regional catalogs are inherited from parents; these are informational findings. Use of the global fallback is reported separately and can be made strict.

```powershell
.\gradlew.bat :devkit-localization-cli:run --args="--translations ../examples/bukkit-commands-example/src/main/resources/lang --references ../examples/bukkit-commands-example/build/generated/minecraftdevkit/text-references.json --base-locale en --locales en,en-GB,de,de-AT --fallback-locale en --resource-format yaml --format json --fail-on MISSING_FALLBACK,PLACEHOLDER_MISMATCH,INVALID_RESOURCE --output ../build/reports/localization.json"
```

The path arguments are relative to `devkit-localization-cli`. `--resource-format` accepts `auto`, `properties`, or `yaml`; `auto` also supports a directory containing both formats. `--disable-default-fallback` makes coverage checks ignore the configured global fallback. Exit codes are `0` for non-strict findings, `1` for configured strict findings, and `2` for invalid arguments or unreadable inputs.

The existing Gradle plugin task `scanDevKitTranslations` accepts the same `.properties` and `.yml` sources. Configure `defaultFallbackLocale` and `defaultFallbackEnabled`; source files and manifests are declared inputs, and the JSON report is written under `build/reports/minecraftdevkit`.

## Modules and dependencies

```kotlin
implementation("com.trickshotmlg.devkit:devkit-localization:<version>")
implementation("com.trickshotmlg.devkit:devkit-localization-yaml:<version>")
```

`devkit-localization-yaml` exposes `devkit-localization` transitively and uses SnakeYAML 2.7 internally. The localization service has no Bukkit dependency. The caller passes a locale; per-user language selection and persistence are not part of this module.
