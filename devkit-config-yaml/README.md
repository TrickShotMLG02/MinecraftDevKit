# devkit-config-yaml

YAML file adapter for `devkit-config`. It uses SnakeYAML 2.7's `SafeConstructor`; the core configuration module has no YAML dependency.

## Load or create

```java
Path path = dataDirectory.resolve("config.yml");
YamlConfigHandle config = YamlConfigHandle.createIfMissing(path, FriendConfig.SCHEMA);
int maximum = config.store().get(FriendConfig.MAX_FRIENDS);
boolean requests = config.store().get(FriendConfig.REQUESTS_ENABLED);
```

`createIfMissing` creates deterministic nested YAML from schema defaults only if the target is absent. It does not overwrite an existing document. A schema with a required key that has no default cannot produce a defaults document. Generated files begin with a deterministic comment index of key paths and their descriptions. YAML uses `config-version` as reserved root metadata. Nested mappings map to dotted Java key paths; YAML mapping keys themselves may not contain dots.

The parser reads UTF-8, limits the document to 1 MiB by default, limits nesting to 32 and collection aliases to 20, rejects duplicate keys, recursive aliases, unsupported object tags, non-mapping roots, and invalid typed values. Quote strings that YAML could otherwise interpret as booleans, numbers, or null. Known explicit YAML null values are invalid. Lists and maps are available through typed codecs.

Unknown values are retained by default and produce warning findings. Select `UnknownKeyPolicy.PRESERVE` to keep them silently or `REJECT` to make them errors. They are carried through saves. Multiple configuration files use independent handles and schemas; they are never implicitly merged.

## Updates, save, and reload

```java
config.store().set(FriendConfig.MAX_FRIENDS, 200); // memory only
YamlSaveResult save = config.save(YamlSaveOptions.safeRewrite());
YamlReloadResult reload = config.reload();
```

Save fingerprints the exact bytes loaded or written by this handle. Unexpected external modification returns a conflict and leaves the file untouched. `YamlSaveOptions` makes the external-change policy explicit; `safeRewrite()` rejects conflicts. `OVERWRITE` explicitly replaces external content. Save serializes one immutable store snapshot to a same-directory temporary file, flushes and validates it, then requires an atomic replacement move. If the filesystem does not support atomic replacement, saving fails and the old file remains. Failed saves keep the store dirty. A small race remains between the final external-change check and atomic replacement because unrelated processes do not participate in a shared lock.

SnakeYAML does not round-trip comments or formatting. Full-document rewriting is explicit through `save(YamlSaveOptions.safeRewrite())`; comments and layout are normalized on a successful save. Unknown fields are preserved, but comments are not. Do not use this save policy for files whose comments must remain intact.

Reload parses and validates a detached candidate before atomic publication. Failure leaves the active snapshot intact. By default, dirty in-memory values reject reload; explicitly choose `DISCARD_PENDING_CHANGES` to replace them. A deleted file is a failed reload and does not empty the active store. An asynchronous variant accepts a caller-owned executor:

```java
config.reloadAsync(YamlReloadPolicy.REJECT_IF_DIRTY, ioExecutor)
    .thenAccept(result -> { /* dispatch platform work to its required thread */ });
```

The framework never owns or shuts down that executor. `ConfigStore` listeners run on the publishing thread; callers must dispatch platform-specific work themselves.

## Optional watcher

```java
config.startWatching(Duration.ofMillis(200),
    result -> logger.info("Config reload: " + result),
    error -> logger.warning("Config watcher failed: " + error));
// on shutdown
config.close();
```

Watching is opt-in and monitors the target's parent directory, including atomic-save replacements. Events are debounced and re-enter the same reload pipeline. A dirty store rejects watcher reloads rather than losing programmatic edits. WatchService event delivery varies by filesystem; manual reload remains the reliable fallback. Each active handle owns one daemon event thread and one single-thread scheduled worker, both closed by `close()`.

## Migrations

```java
ConfigMigrationRegistry migrations = ConfigMigrationRegistry.builder()
    .register(1, 2, values -> values.move("friends.max-count", "friends.max"))
    .build();
YamlConfigHandle config = YamlConfigHandle.load(path, FriendConfig.SCHEMA,
    UnknownKeyPolicy.PRESERVE_AND_WARN, migrations);
```

Migrations are contiguous one-version steps over a detached dotted-path candidate. Missing steps, future versions, collisions, exceptions, or invalid migrated values reject loading without editing the original. Migration callback exception messages and causes are omitted from diagnostics because callbacks can include raw credentials in them; the failing version step and exception type are reported instead. A migrated handle starts dirty. Its first successful save creates a non-overwriting `config.yml.vN.bak` source backup and only then atomically replaces the document with the current schema version. A matching existing backup can be reused on retry; a different backup is never overwritten.

Schema version 1 is assumed when `config-version` is absent. The metadata key is emitted by generated defaults and successful saves. Migration does not automatically persist during load.

## Sensitive values

Mark credentials with `.sensitive()`. Validation findings, default validation failures, programmatic codec failures, and YAML encoding failures redact source values and unsafe validator/codec messages for those keys. Cross-field validator exceptions are reported generically because they may inspect any value. Values still must be persisted when configured. Local YAML files are not encrypted or a secure secret vault; use filesystem permissions and platform secret facilities where appropriate.
