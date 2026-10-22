package com.trickshotmlg.devkit.bukkit.gui;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SkullTextureServiceTest {
    @Test
    void onlyMinecraftTextureHashesAndUrlsAreAccepted() {
        String hash = "0123456789abcdef0123456789abcdef";
        assertEquals("https://textures.minecraft.net/texture/" + hash,
                SkullTextureService.validateTextureUrl(hash).toExternalForm());
        assertEquals("https://textures.minecraft.net/texture/" + hash,
                SkullTextureService.validateTextureUrl("https://textures.minecraft.net/texture/" + hash).toExternalForm());
        assertThrows(IllegalArgumentException.class, () -> SkullTextureService.validateTextureUrl("http://textures.minecraft.net/texture/" + hash));
        assertThrows(IllegalArgumentException.class, () -> SkullTextureService.validateTextureUrl("https://evil.example/texture/" + hash));
        assertThrows(IllegalArgumentException.class, () -> SkullTextureService.validateTextureUrl("https://textures.minecraft.net.evil.example/texture/" + hash));
    }

    @Test
    void base64TexturePayloadMustContainStructuredSkinUrl() {
        String hash = "0123456789abcdef0123456789abcdef";
        String payload = Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/" + hash + "\"}}}")
                .getBytes(StandardCharsets.UTF_8));
        assertEquals("https://textures.minecraft.net/texture/" + hash,
                SkullTextureService.validateBase64Texture(payload).toExternalForm());
        assertThrows(IllegalArgumentException.class, () -> SkullTextureService.validateBase64Texture("not-base64!"));
        String malicious = Base64.getEncoder().encodeToString("{\"url\":\"https://evil.example/\"}".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> SkullTextureService.validateBase64Texture(malicious));
        assertInvalidPayload("not-json \"url\":\"https://textures.minecraft.net/texture/" + hash + "\"");
        assertInvalidPayload("{\"other\":{\"url\":\"https://textures.minecraft.net/texture/" + hash + "\"}}");
        assertInvalidPayload("{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/" + hash + "\"},\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/" + hash + "\"}}}");
        assertInvalidPayload("{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/" + hash + "\"}}} trailing");
    }

    private static void assertInvalidPayload(String json) {
        String payload = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> SkullTextureService.validateBase64Texture(payload), json);
    }
}
