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
public final class YamlConfigHandle {
    private final Path file;
    private final YamlConfigFile yaml;
    private final ConfigStore store;
    private volatile Map<String, Object> unknownValues;
    private volatile String diskFingerprint;

    private YamlConfigHandle(Path file, YamlConfigFile yaml, YamlConfigLoadResult loaded, String fingerprint) {
        this.file = file.toAbsolutePath().normalize();
        this.yaml = yaml;
        store = loaded.store();
        unknownValues = loaded.unknownValues();
        diskFingerprint = fingerprint;
    }

    /** Opens and validates an existing configuration file. */
    public static YamlConfigHandle load(Path file, ConfigSchema schema) throws IOException {
        return load(file, new YamlConfigFile(schema));
    }
    /** Opens with an explicitly configured unknown-key policy. */
    public static YamlConfigHandle load(Path file, ConfigSchema schema, UnknownKeyPolicy policy) throws IOException {
        return load(file, new YamlConfigFile(schema, policy));
    }
    private static YamlConfigHandle load(Path file, YamlConfigFile yaml) throws IOException {
        Path normalized = file.toAbsolutePath().normalize();
        YamlConfigLoadResult result = yaml.load(normalized);
        return new YamlConfigHandle(normalized, yaml, result, fingerprint(normalized));
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
        long savedGeneration = store.generation();
        try {
            String currentFingerprint = fingerprint(file);
            if (!Objects.equals(diskFingerprint, currentFingerprint)
                    && options.externalChangePolicy() == YamlSaveOptions.ExternalChangePolicy.REJECT) {
                return new YamlSaveResult(false, false, true, file, savedGeneration, store.generation(),
                        java.util.Optional.of("configuration file changed outside this process"));
            }
            byte[] bytes = yaml.serialize(store, unknownValues).getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    return failure(false, "filesystem does not support atomic replacement; original file was retained");
                }
            } finally { Files.deleteIfExists(temporary); }
            diskFingerprint = fingerprint(file);
            try { store.markPersisted(savedGeneration); } catch (IllegalStateException concurrentChange) {
                return new YamlSaveResult(true, true, false, file, savedGeneration, store.generation(),
                        java.util.Optional.of("saved this generation; a newer in-memory generation remains dirty"));
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
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
}
