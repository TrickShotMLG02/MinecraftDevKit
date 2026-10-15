# devkit-text

`devkit-text` contains immutable message references and a platform-independent rendering contract. It uses Kyori Adventure `Component` for rendered messages, which is compatible with the shared Java 17 baseline through Adventure 4.x.

## Quick start

```java
TextRef literal = TextRef.literal("Manage your friends");
TextRef keyOnly = TextRef.translated("commands.friends.description");
TextRef withFallback = TextRef.translated(
        "commands.friends.description",
        "Manage your friends"
);

TextProvider provider = new StaticTextProvider(MissingTranslationPolicy.SHOW_KEY);
Component message = provider.resolve(withFallback, Locale.US);
```

A literal remains literal. Translation keys are explicit; arbitrary strings are never interpreted as keys. Translation keys use a conservative character set: letters and digits, followed by letters, digits, dots, underscores, or hyphens. A missing-key fallback is available to the renderer without changing the reference's translation key, so localization diagnostics can still report it.

`StaticTextProvider` is useful when no translation catalog is configured. It returns the fallback for translated references, otherwise it follows `MissingTranslationPolicy` (`SHOW_KEY`, `SHOW_MARKER`, or `ERROR`). Localization-aware diagnostics and resource loading are supplied by `devkit-localization`.

Providers resolve at message time with a requested `Locale`. Placeholder values are inserted as plain component text, not parsed as MiniMessage or other markup. Use doubled braces (`{{name}}`) to display placeholder syntax literally. Unprovided placeholders remain visible for later diagnosis by the localization provider.

## Dependency

```kotlin
implementation("com.trickshotmlg.devkit:devkit-text:<version>")
```

The module exposes Adventure API as an `api` dependency because its public provider contract returns `Component`. MinecraftDevKit remains GPL-3.0-only; Adventure remains a separate MIT-licensed library dependency.
