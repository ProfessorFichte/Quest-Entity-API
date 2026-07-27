package com.qeapi.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Replaces {placeholder_name}-style placeholders in text.
public final class TextMutator {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");

    private TextMutator() {}

    public static Component mutate(Component component, Map<String, String> values) {
        String text = component.getString();
        String mutated = mutatePlaceholders(text, values);
        return Component.literal(mutated);
    }

    public static String mutatePlaceholders(String text, Map<String, String> values) {
        if (text == null || text.isEmpty() || values == null || values.isEmpty()) {
            return text;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = values.getOrDefault(placeholder, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
