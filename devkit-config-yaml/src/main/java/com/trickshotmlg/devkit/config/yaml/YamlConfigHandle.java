package com.trickshotmlg.devkit.config.yaml;

import com.trickshotmlg.devkit.config.ConfigSchema;
import com.trickshotmlg.devkit.config.ConfigStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.HexFormat;

/** Long-lived YAML configuration handle with explicit, conflict-aware atomic persistence. */
public final class YamlConfigHandle implements AutoCloseable {
    private final Path file;
    private final YamlConfigFile yaml;
    private final ConfigStore store;
    private volatile Map<String, Object> unknownValues;
    private volatile String diskFingerprint;
    private volatile YamlConfigWatcher watcher;
    private volatile boolean migrationPending;
    private volatile int sourceVersion;

    private YamlConfigHandle(Path file, YamlConfigFile yaml, YamlConfigLoadResult loaded) {
        this.file = file.toAbsolutePath().normalize();
        this.yaml = yaml;
        store = loaded.store();
        unknownValues = loaded.unknownValues();
        diskFingerprint = loaded.sourceFingerprint().orElseThrow(
                () -> new IllegalArgumentException("Loaded YAML result has no source fingerprint"));
        migrationPending = loaded.migrated();
        sourceVersion = loaded.sourceVersion();
        if (migrationPending) store.markUnsaved();
    }

    /** Opens and validates an existing configuration file. */
    public static YamlConfigHandle load(Path file, ConfigSchema schema) throws IOException {
        return load(file, new YamlConfigFile(schema));
    }
    /** Opens with an explicitly configured unknown-key policy. */
    public static YamlConfigHandle load(Path file, ConfigSchema schema, UnknownKeyPolicy policy) throws IOException {
        return load(file, new YamlConfigFile(schema, policy));
    }
    /** Opens with explicit unknown-key and migration policies. */
    public static YamlConfigHandle load(Path file, ConfigSchema schema, UnknownKeyPolicy policy,
                                        ConfigMigrationRegistry migrations) throws IOException {
        return load(file, new YamlConfigFile(schema, policy, migrations));
    }
    private static YamlConfigHandle load(Path file, YamlConfigFile yaml) throws IOException {
        Path normalized = file.toAbsolutePath().normalize();
        YamlConfigLoadResult result = yaml.load(normalized);
        return new YamlConfigHandle(normalized, yaml, result);
    }

    /** Creates default YAML only when missing, then opens it; existing files are never overwritten. */
    public static YamlConfigHandle createIfMissing(Path file, ConfigSchema schema) throws IOException {
        YamlConfigFile yaml = new YamlConfigFile(schema);
        yaml.createDefaults(file);
        return load(file, yaml);
    }

    public Path file() { return file; }
    public ConfigStore store() { return store; }
    public Map<String, Object> unknownValues() { return unknownValues; }

    /** Reloads the full file and publishes it only when parsing and schema validation both pass. */
    public synchronized YamlReloadResult reload() { return reload(YamlReloadPolicy.REJECT_IF_DIRTY); }

    /** Reloads with an explicit policy for pending in-memory changes. */
    public synchronized YamlReloadResult reload(YamlReloadPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        long expectedGeneration = store.generation();
        if (store.isDirty() && policy == YamlReloadPolicy.REJECT_IF_DIRTY) {
            return reloadFailure(true, "configuration has unsaved in-memory changes", java.util.List.of());
        }
        try {
            YamlConfigLoadResult candidate = yaml.load(file);
            boolean changed = !store.snapshot().values().equals(candidate.store().snapshot().values())
                    || !unknownValues.equals(candidate.unknownValues());
            store.replaceFromReload(candidate.store().snapshot().values(), expectedGeneration);
            unknownValues = candidate.unknownValues();
            diskFingerprint = candidate.sourceFingerprint().orElseThrow(
                    () -> new IllegalStateException("Loaded YAML result has no source fingerprint"));
            migrationPending = candidate.migrated();
            sourceVersion = candidate.sourceVersion();
            if (migrationPending) store.markUnsaved();
            return new YamlReloadResult(true, changed, false, file, store.generation(), candidate.issues(), java.util.Optional.empty());
        } catch (java.util.ConcurrentModificationException exception) {
            return reloadFailure(true, exception.getMessage(), java.util.List.of());
        } catch (IOException | RuntimeException exception) {
            java.util.List<com.trickshotmlg.devkit.config.ConfigIssue> issues = exception instanceof YamlConfigException yamlException
                    ? yamlException.issues() : exception instanceof com.trickshotmlg.devkit.config.ConfigValidationException validation
                    ? validation.issues() : java.util.List.of();
            return reloadFailure(false, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(), issues);
        }
    }

    /** Performs reload work on the caller-provided executor, whose lifecycle remains caller-owned. */
    public java.util.concurrent.CompletionStage<YamlReloadResult> reloadAsync(YamlReloadPolicy policy,
                                                                               java.util.concurrent.Executor executor) {
        Objects.requireNonNull(executor, "executor");
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> reload(policy), executor);
    }

    /** Starts an opt-in watcher for this file; callbacks run on the watch reload worker. */
    public synchronized void startWatching(java.time.Duration debounce,
                                           java.util.function.Consumer<YamlReloadResult> resultConsumer,
                                           java.util.function.Consumer<Throwable> failureConsumer) throws IOException {
        if (watcher != null) throw new IllegalStateException("Configuration file watching is already active");
        YamlConfigWatcher candidate = new YamlConfigWatcher(this, debounce, resultConsumer, failureConsumer);
        try {
            candidate.start();
            watcher = candidate;
        } catch (IOException | RuntimeException exception) {
            candidate.close();
            throw exception;
        }
    }

    /** Stops the optional file watcher and releases its thread and WatchService. */
    public synchronized void stopWatching() {
        YamlConfigWatcher current = watcher;
        watcher = null;
        if (current != null) current.close();
    }

    @Override public void close() { stopWatching(); }

    private YamlReloadResult reloadFailure(boolean conflict, String message,
                                           java.util.List<com.trickshotmlg.devkit.config.ConfigIssue> issues) {
        return new YamlReloadResult(false, false, conflict, file, store.generation(), issues, java.util.Optional.ofNullable(message));
    }

    /**
     * Saves a complete normalized YAML document through a same-directory temporary file and an
     * atomic replace. This policy rewrites comments and formatting. The caller must select that
     * document policy via {@link YamlSaveOptions#safeRewrite()}.
     */
    public synchronized YamlSaveResult save(YamlSaveOptions options) {
        Objects.requireNonNull(options, "options");
        if (options.documentPolicy() != YamlSaveOptions.DocumentPolicy.REWRITE_DOCUMENT) {
            return failure(false, "unsupported document policy");
        }
        com.trickshotmlg.devkit.config.ConfigSnapshot saveSnapshot = store.snapshot();
        long savedGeneration = saveSnapshot.generation();
        try {
            String currentFingerprint = fingerprint(file);
            if (!Objects.equals(diskFingerprint, currentFingerprint)
                    && options.externalChangePolicy() == YamlSaveOptions.ExternalChangePolicy.REJECT) {
                return new YamlSaveResult(false, false, true, file, savedGeneration, store.generation(),
                        java.util.Optional.of("configuration file changed outside this process"));
            }
            byte[] bytes = yaml.serialize(saveSnapshot, unknownValues).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, file.getFileName().toString() + ".", ".tmp");
            try {
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                // Validate the exact serialized document before it can replace the active file.
                Path verification = temporary;
                yaml.load(verification);
                if (migrationPending) {
                    createMigrationBackup(sourceVersion);
                }
                if (!Objects.equals(currentFingerprint, fingerprint(file))
                        && options.externalChangePolicy() == YamlSaveOptions.ExternalChangePolicy.REJECT) {
                    return new YamlSaveResult(false, false, true, file, savedGeneration, store.generation(),
                            java.util.Optional.of("configuration file changed while the save was being prepared"));
                }
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    return failure(false, "filesystem does not support atomic replacement; original file was retained");
                }
            } finally { Files.deleteIfExists(temporary); }
            diskFingerprint = fingerprint(bytes);
            migrationPending = false;
            sourceVersion = yaml.schemaVersion();
            try { store.markPersisted(savedGeneration); } catch (IllegalStateException concurrentChange) {
                return new YamlSaveResult(true, true, false, file, savedGeneration, store.generation(),
                        java.util.Optional.empty());
            }
            return new YamlSaveResult(true, true, false, file, savedGeneration, store.generation(), java.util.Optional.empty());
        } catch (IOException | RuntimeException exception) {
            return failure(false, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }
    }

    private YamlSaveResult failure(boolean conflict, String error) {
        return new YamlSaveResult(false, false, conflict, file, store.generation(), store.generation(), java.util.Optional.of(error));
    }

    private static String fingerprint(Path file) throws IOException {
        if (!Files.exists(file)) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }

    private static String fingerprint(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }

    private void createMigrationBackup(int version) throws IOException {
        Path backup = file.resolveSibling(file.getFileName() + ".v" + version + ".bak");
        if (Files.exists(backup)) {
            if (Objects.equals(fingerprint(file), fingerprint(backup))) return;
            throw new IOException("Migration backup already exists and differs from the source; refusing to overwrite " + backup.getFileName());
        }
        Path temporaryBackup = Files.createTempFile(file.getParent(), backup.getFileName().toString(), ".tmp");
        try {
            Files.copy(file, temporaryBackup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            try { Files.move(temporaryBackup, backup, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Filesystem does not support atomic migration backup creation", exception);
            }
        } finally { Files.deleteIfExists(temporaryBackup); }
    }
}
