# devkit-bukkit-feedback

Common Bukkit player feedback using Adventure `Component` input. Without an optional provider it serializes to Spigot-compatible legacy chat/action-bar/title text. When `devkit-paper-gui` is present, its small provider sends chat, action bars, and titles as native Paper Adventure components. Boss bars use the common Bukkit BossBar API on both platforms. A configured `TextProvider` resolves `TextRef` using the player's client locale; the existing provider handles fallback and inserts placeholder values safely.

```java
try (BukkitFeedback feedback = BukkitFeedback.create(plugin, localization)) {
    feedback.message(player, TextRef.translated("welcome", "Welcome, {name}"), Map.of("name", player.getName()));
    feedback.actionBar(player, Component.text("Ready"));
    feedback.title(player, Component.text("Hello"), Component.empty(),
        FeedbackTimings.of(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(500)));
    feedback.sound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
}
```

Boss bars are owned handles: close them explicitly or close the feedback service. Viewers are removed on disconnect; all bars are removed on plugin disable. Calls operate immediately in the caller's Bukkit context and should be made on a thread safe for the player/server API. Spigot output preserves supported legacy colors and decorations. Sound resource-pack identifiers only work when the client has the matching sound resource.
