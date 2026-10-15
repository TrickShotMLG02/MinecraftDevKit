package com.trickshotmlg.devkit.localization;

import com.trickshotmlg.devkit.text.TextFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.ArrayList;
import java.util.Map;

/** Renders trusted catalog markup while inserting dynamic values as non-parsed components. */
final class TranslationTemplateRenderer {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private TranslationTemplateRenderer() { }

    static Component render(String template, Map<String, ?> values, boolean markup) {
        if (!markup) return TextFormatter.render(template, values);
        StringBuilder prepared = new StringBuilder(template.length());
        ArrayList<TagResolver> resolvers = new ArrayList<>();
        int placeholderIndex = 0;
        for (int index = 0; index < template.length();) {
            char current = template.charAt(index);
            if (current == '{' && index + 1 < template.length() && template.charAt(index + 1) == '{') {
                prepared.append('{');
                index += 2;
                continue;
            }
            if (current == '}' && index + 1 < template.length() && template.charAt(index + 1) == '}') {
                prepared.append('}');
                index += 2;
                continue;
            }
            if (current == '{') {
                int closing = template.indexOf('}', index + 1);
                if (closing > index + 1) {
                    String name = template.substring(index + 1, closing);
                    if (TextFormatter.placeholders("{" + name + "}").contains(name) && values.containsKey(name)) {
                        String tag = "__mdkph_" + placeholderIndex++;
                        while (template.contains("<" + tag + ">")) tag = "__mdkph_" + placeholderIndex++;
                        prepared.append('<').append(tag).append('>');
                        resolvers.add(Placeholder.component(tag, Component.text(String.valueOf(values.get(name)))));
                        index = closing + 1;
                        continue;
                    }
                }
            }
            prepared.append(current);
            index++;
        }
        TagResolver.Builder resolver = TagResolver.builder();
        resolvers.forEach(resolver::resolver);
        return MINI_MESSAGE.deserialize(prepared.toString(), resolver.build());
    }
}
