# devkit-bukkit-items

Reusable item building and immutable templates independent of inventory GUI modules. The existing `devkit-bukkit-gui` `ItemBuilder` remains as a source-compatible facade over this builder.

```java
DataKey<String> ITEM_ID = DataKey.string(plugin, "item_id");
ItemTemplate voucher = ItemTemplate.builder(Material.PAPER)
    .localizedName(TextRef.translated("voucher.name", "Voucher"))
    .localizedLore(List.of(TextRef.translated("voucher.lore", "Redeem once")))
    .data(ITEM_ID, "welcome")
    .build();
ItemStack copy = voucher.create(localization, playerLocale, Map.of());
```

Templates snapshot their prototype at build time and each `create` returns an independent stack. Localization is rendered per call, so do not cache one viewer's localized item for everyone. `GuiItems.template(template)` adapts it to the existing viewer-specific GUI render API. Common components use legacy serialization; the optional Paper GUI provider preserves native Adventure name/lore components. Bukkit item/world mutation remains subject to normal server-thread and save semantics.
