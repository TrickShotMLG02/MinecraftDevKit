# devkit-paper-gui

Optional Paper 1.21.11 enhancements for `devkit-bukkit-gui`. The common GUI module compiles against Spigot API and provides legacy-component serialization and string inventory titles on Spigot. This module installs a GUI platform extension that preserves native Adventure item names, lore, and inventory titles.

Keep this module out of plugins intended to run on Spigot. On Paper, add it as a runtime dependency alongside `devkit-bukkit-gui`; its service provider is discovered automatically. `GuiPlatformCapabilities` reports whether native Adventure metadata is available. Item-model metadata is provided by the common Spigot API.

The module is compiled against Paper API `1.21.11-R0.1-SNAPSHOT`, Java 21, and does not bundle Paper classes. Other Paper versions and live server behavior have not been verified.
