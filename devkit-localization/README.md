# devkit-localization

`devkit-localization` loads immutable UTF-8 `.properties` catalogs and resolves `TextRef` values for the sender's locale. It is independent of Bukkit and does not provide runtime player state or a global translator.

## Resource format

Use one `<language-tag>.properties` resource per locale, for example `translations/en-US.properties` and `translations/de-DE.properties`:

```properties
friends.add.success=Added {player} as a friend.
```

Classpath loading takes the locales explicitly so missing files are reported instead of silently omitted:

```java
ResourceBasedTextProvider provider = ResourceBasedTextProvider.fromClasspath(
        FriendNetPlugin.class.getClassLoader(),
        "translations",
        List.of(Locale.US, Locale.GERMANY),
        Locale.US,
        MissingTranslationPolicy.SHOW_KEY,
        diagnostic -> logger.warning(diagnostic.toString())
);
Component message = provider.resolve(
        TextRef.translated("friends.add.success"),
        playerLocale,
        Map.of("player", playerName),
        Optional.of("friends.add.success")
);
```

`fromDirectory` supports tools and tests that load translation files outside a server. Filenames must be valid BCP 47 tags followed by `.properties`. Java properties escape sequences are supported; resource files are decoded as UTF-8.

## Resolution and diagnostics

Lookup order is exact requested locale, requested language-only locale, configured default locale, default language-only locale, then the `TextRef` fallback. A missing requested-locale entry is reported even when a fallback translation or reference fallback supplies the displayed text. If no text can be found, `SHOW_KEY`, `SHOW_MARKER`, or `ERROR` controls behavior.

`TextDiagnostic` reports the translation key, requested locale, reference-fallback availability, resolution outcome, optional unresolved placeholder, and optional source context. Events are deduplicated by kind, key, locale, and placeholder for the lifetime of the provider. A custom `TextDiagnosticSink` can bridge diagnostics to an application's logger, metrics, or tests.

Named placeholders use `{name}` with names beginning with a letter and containing letters, digits, dots, underscores, or hyphens. Doubled braces escape placeholder syntax. Replacement values are inserted as plain Adventure text and are never parsed as formatting markup. Missing placeholder values remain visible and generate a diagnostic.

Translation catalogs and loaded provider configuration are immutable. Create a new provider to reload changed resource files; per-locale resolved output is not cached, so one registered command can serve players in different locales.

## Dependency

```kotlin
implementation("com.trickshotmlg.devkit:devkit-localization:<version>")
```

The module exposes `devkit-text` transitively. See the [text API guide](../devkit-text/README.md).
