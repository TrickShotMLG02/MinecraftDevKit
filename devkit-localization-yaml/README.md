# devkit-localization-yaml

YAML translation sources for MinecraftDevKit. The module uses SnakeYAML's safe constructor and exposes immutable `TranslationCatalog` values through the shared `TranslationSource` contract.

## External files

`YamlTranslationSource.directory(path)` reads UTF-8 `.yml` files in deterministic path order. A root file such as `en-GB.yml` and files under `en-GB/*.yml` contribute to the same locale. File names inside a locale directory do not prefix keys; YAML mappings declare complete keys:

```yaml
commands:
  friends:
    add:
      success: "Added {player}."
```

This creates `commands.friends.add.success`. Same-source duplicate keys, including duplicates split across files, fail instead of depending on directory order. Missing external directories return an empty source so first startup can create them later.

## Bundled resources

`YamlTranslationSource.bundled("lang", "en", "en-GB", "de")` loads `<locale>.yml` resources from the classpath. To bundle multiple files for one locale, add `<locale>/index.txt` listing the locale directory's YAML file names, one per line. The explicit index works from both directories and JAR files.

```text
lang/en/index.txt
lang/en/commands.yml
lang/en/friends.yml
```

The index is UTF-8. Blank lines and lines beginning with `#` are ignored. Index entries must be simple `.yml` file names. Translation keys are never inferred from file names.

Only mappings and string leaf values are accepted. Quote values that YAML could parse as booleans, numbers, or null. The parser rejects duplicate YAML keys, unsupported tags, aliases beyond the configured safe limit, nesting deeper than 32 levels, and files larger than 1 MiB.

This module only parses source files. Locale fallback, source precedence, live reload, and runtime diagnostics belong to `devkit-localization` and are added through its shared service API.
