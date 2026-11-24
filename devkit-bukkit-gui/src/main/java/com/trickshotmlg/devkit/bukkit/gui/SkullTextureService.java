package com.trickshotmlg.devkit.bukkit.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Resolves offline player profiles asynchronously and prepares safe custom texture profiles.
 * The server API owns the profile update executor; this service creates no worker threads. */
public final class SkullTextureService implements AutoCloseable {
    private static final int MAX_PAYLOAD_BYTES = 16_384;
    private final Plugin plugin;
    private final int maximumCachedProfiles;
    private final Map<UUID, PlayerProfile> cache;
    private final ConcurrentMap<UUID, CompletableFuture<PlayerProfile>> inFlight = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public SkullTextureService(Plugin plugin) { this(plugin, 512); }
    public SkullTextureService(Plugin plugin, int maximumCachedProfiles) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        if (maximumCachedProfiles < 1) throw new IllegalArgumentException("maximumCachedProfiles must be positive");
        this.maximumCachedProfiles = maximumCachedProfiles;
        cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<UUID, PlayerProfile> eldest) {
                return size() > SkullTextureService.this.maximumCachedProfiles;
            }
        };
    }

    /** Returns a completed future for a cached profile or deduplicates the server API's asynchronous profile lookup. */
    public CompletionStage<PlayerProfile> resolve(UUID uuid) {
        requireMainThread();
        Objects.requireNonNull(uuid, "uuid");
        ensureOpen();
        synchronized (cache) {
            PlayerProfile cached = cache.get(uuid);
            if (cached != null) return CompletableFuture.completedFuture(cached.clone());
        }
        CompletableFuture<PlayerProfile> candidate = new CompletableFuture<>();
        CompletableFuture<PlayerProfile> existing = inFlight.putIfAbsent(uuid, candidate);
        if (existing != null) return existing.thenApply(PlayerProfile::clone);
        try {
            Bukkit.createPlayerProfile(uuid).update().whenComplete((profile, failure) -> {
                inFlight.remove(uuid, candidate);
                if (closed) {
                    candidate.cancel(false);
                } else if (failure != null) {
                    candidate.completeExceptionally(failure);
                } else if (profile == null || profile.getTextures().isEmpty()) {
                    candidate.completeExceptionally(new IllegalStateException("No skin texture is available for " + uuid));
                } else {
                    synchronized (cache) { cache.put(uuid, profile.clone()); }
                    candidate.complete(profile.clone());
                }
            });
        } catch (Throwable failure) {
            inFlight.remove(uuid, candidate);
            candidate.completeExceptionally(failure);
        }
        return candidate.thenApply(PlayerProfile::clone);
    }

    /** Returns a defensive cached profile when resolved. */
    public Optional<PlayerProfile> cached(UUID uuid) {
        synchronized (cache) {
            PlayerProfile profile = cache.get(Objects.requireNonNull(uuid, "uuid"));
            return profile == null ? Optional.empty() : Optional.of(profile.clone());
        }
    }

    /** Creates a skull item using an online player's already available profile without network access. */
    public static org.bukkit.inventory.ItemStack onlineHead(Player player) {
        Objects.requireNonNull(player, "player");
        return ItemBuilder.of(org.bukkit.Material.PLAYER_HEAD).skullProfile(player.getPlayerProfile()).build();
    }

    /** Creates a custom-texture player head. Only a texture hash or textures.minecraft.net URL is accepted. */
    public static org.bukkit.inventory.ItemStack customHead(String textureHashOrUrl) {
        return head(textureProfile(textureHashOrUrl));
    }

    /** Prepares a Bukkit player profile containing a validated custom skin URL. */
    public static PlayerProfile textureProfile(String hashOrUrl) {
        URL url = validateTextureUrl(hashOrUrl);
        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
        PlayerTextures textures = profile.getTextures();
        textures.setSkin(url);
        profile.setTextures(textures);
        return profile;
    }

    /** Parses Mojang's base64 texture property without accepting arbitrary URLs or payload sizes. */
    public static URL validateBase64Texture(String encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length() > MAX_PAYLOAD_BYTES * 2) throw new IllegalArgumentException("Texture payload is too large");
        final byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException failure) { throw new IllegalArgumentException("Texture payload is not valid Base64", failure); }
        if (bytes.length > MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("Texture payload is too large");
        final String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException("Texture payload is not valid UTF-8", failure);
        }
        return validateTextureUrl(TexturePayloadParser.skinUrl(json));
    }

    public static URL validateTextureUrl(String hashOrUrl) {
        Objects.requireNonNull(hashOrUrl, "hashOrUrl");
        String candidate = hashOrUrl.matches("[a-fA-F0-9]{32}")
                ? "https://textures.minecraft.net/texture/" + hashOrUrl : hashOrUrl;
        try {
            URI uri = URI.create(candidate);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"textures.minecraft.net".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null
                    || !uri.getPath().matches("/texture/[a-fA-F0-9]{32}"))
                throw new IllegalArgumentException("Texture URL must be https://textures.minecraft.net/texture/<32-hex-hash>");
            return uri.toURL();
        } catch (IllegalArgumentException | java.net.MalformedURLException failure) {
            if (failure instanceof IllegalArgumentException argument) throw argument;
            throw new IllegalArgumentException("Invalid texture URL", failure);
        }
    }

    /** Creates a player head from a resolved profile. Call on the server thread. */
    public static org.bukkit.inventory.ItemStack head(PlayerProfile profile) {
        return ItemBuilder.of(org.bukkit.Material.PLAYER_HEAD).skullProfile(Objects.requireNonNull(profile, "profile")).build();
    }

    public int cachedProfileCount() { synchronized (cache) { return cache.size(); } }
    public int pendingLookupCount() { return inFlight.size(); }
    private void ensureOpen() { if (closed) throw new IllegalStateException("SkullTextureService is closed"); }
    private static void requireMainThread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Profile lookup must be started on the server thread"); }
    @Override public void close() {
        closed = true;
        inFlight.values().forEach(future -> future.cancel(false));
        inFlight.clear();
        synchronized (cache) { cache.clear(); }
    }
}
