package com.trickshotmlg.devkit.bukkit.items;

import com.trickshotmlg.devkit.text.TextProvider;
import com.trickshotmlg.devkit.text.TextRef;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Immutable reusable item definition. Each render produces an independent ItemStack. */
public final class ItemTemplate {
    private final ItemStack prototype;
    private final TextRef localizedName;
    private final List<TextRef> localizedLore;

    private ItemTemplate(Builder builder) {
        prototype = builder.item.build();
        localizedName = builder.localizedName;
        localizedLore = List.copyOf(builder.localizedLore);
    }

    public static Builder builder(Material material) { return new Builder(ItemBuilder.of(material)); }
    public Builder toBuilder() {
        Builder builder = new Builder(ItemBuilder.from(prototype));
        builder.localizedName = localizedName;
        builder.localizedLore.addAll(localizedLore);
        return builder;
    }
    public ItemStack create() { return create(reference -> {
        if (reference instanceof TextRef.Literal literal) return Component.text(literal.value());
        return Component.text(reference.fallback().orElseGet(() -> reference.translationKey().orElse("")));
    }); }
    public ItemStack create(TextProvider provider, java.util.Locale locale, Map<String, ?> placeholders) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(locale, "locale");
        Map<String, ?> safe = Map.copyOf(Objects.requireNonNull(placeholders, "placeholders"));
        return create(reference -> provider.resolve(reference, locale, safe));
    }
    /** Renders localized fields using a caller's per-viewer text resolver, such as a GUI render context. */
    public ItemStack create(Function<TextRef, Component> resolver) {
        Objects.requireNonNull(resolver, "resolver");
        ItemBuilder rendered = ItemBuilder.from(prototype);
        if (localizedName != null) rendered.name(Objects.requireNonNull(resolver.apply(localizedName), "resolved name"));
        if (!localizedLore.isEmpty()) rendered.lore(localizedLore.stream().map(ref -> Objects.requireNonNull(resolver.apply(ref), "resolved lore")).toList());
        return rendered.build();
    }

    public static final class Builder {
        private final ItemBuilder item;
        private TextRef localizedName;
        private final java.util.ArrayList<TextRef> localizedLore = new java.util.ArrayList<>();
        private Builder(ItemBuilder item) { this.item = item; }
        public Builder amount(int amount) { item.amount(amount); return this; }
        public Builder name(Component component) { item.name(component); localizedName = null; return this; }
        public Builder name(String plainText) { item.name(plainText); localizedName = null; return this; }
        public Builder lore(List<Component> lines) { item.lore(lines); localizedLore.clear(); return this; }
        public Builder enchantment(org.bukkit.enchantments.Enchantment enchantment, int level) { item.enchantment(enchantment, level); return this; }
        public Builder glow(boolean enabled) { item.glow(enabled); return this; }
        public Builder unbreakable(boolean enabled) { item.unbreakable(enabled); return this; }
        public Builder hide(org.bukkit.inventory.ItemFlag... flags) { item.hide(flags); return this; }
        public Builder damage(int damage) { item.damage(damage); return this; }
        public Builder customModelData(Integer value) { item.customModelData(value); return this; }
        public Builder itemModel(org.bukkit.NamespacedKey key) { item.itemModel(key); return this; }
        public <T> Builder data(com.trickshotmlg.devkit.bukkit.data.DataKey<T> key, T value) { item.data(key, value); return this; }
        public Builder localizedName(TextRef reference) { localizedName = Objects.requireNonNull(reference, "reference"); return this; }
        public Builder localizedLore(List<TextRef> references) { localizedLore.clear(); localizedLore.addAll(List.copyOf(references)); return this; }
        public ItemTemplate build() { return new ItemTemplate(this); }
    }
}
