package com.trickshotmlg.devkit.bukkit.gui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict, size-bounded JSON reader for the small untrusted Mojang texture payload. */
final class TexturePayloadParser {
    private static final int MAX_DEPTH = 32;

    private TexturePayloadParser() { }

    static String skinUrl(String json) {
        Object parsed = new Parser(json).parse();
        if (!(parsed instanceof Map<?, ?> root)
                || !(root.get("textures") instanceof Map<?, ?> textures)
                || !(textures.get("SKIN") instanceof Map<?, ?> skin)
                || !(skin.get("url") instanceof String url)) {
            throw new IllegalArgumentException("Texture payload must contain a string at textures.SKIN.url");
        }
        return url;
    }

    private static final class Parser {
        private final String input;
        private int offset;

        private Parser(String input) { this.input = input; }

        private Object parse() {
            skipWhitespace();
            Object value = value(0);
            skipWhitespace();
            if (offset != input.length()) fail("Unexpected trailing content");
            return value;
        }

        private Object value(int depth) {
            if (depth > MAX_DEPTH) fail("Texture payload nesting is excessive");
            skipWhitespace();
            if (offset >= input.length()) return fail("Expected a JSON value");
            return switch (input.charAt(offset)) {
                case '{' -> object(depth + 1);
                case '[' -> array(depth + 1);
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object(int depth) {
            offset++;
            skipWhitespace();
            Map<String, Object> result = new LinkedHashMap<>();
            if (consume('}')) return result;
            while (true) {
                skipWhitespace();
                if (offset >= input.length() || input.charAt(offset) != '"') fail("Expected an object key");
                String key = string();
                skipWhitespace();
                require(':');
                Object item = value(depth);
                if (result.containsKey(key)) fail("Duplicate object key " + key);
                result.put(key, item);
                skipWhitespace();
                if (consume('}')) return result;
                require(',');
            }
        }

        private List<Object> array(int depth) {
            offset++;
            skipWhitespace();
            List<Object> result = new ArrayList<>();
            if (consume(']')) return result;
            while (true) {
                result.add(value(depth));
                skipWhitespace();
                if (consume(']')) return result;
                require(',');
            }
        }

        private String string() {
            require('"');
            StringBuilder result = new StringBuilder();
            while (offset < input.length()) {
                char c = input.charAt(offset++);
                if (c == '"') return result.toString();
                if (c < 0x20) fail("Unescaped control character in string");
                if (c != '\\') {
                    result.append(c);
                    continue;
                }
                if (offset >= input.length()) fail("Incomplete string escape");
                char escape = input.charAt(offset++);
                switch (escape) {
                    case '"', '\\', '/' -> result.append(escape);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> result.append(unicodeEscape());
                    default -> fail("Invalid string escape");
                }
            }
            return fail("Unterminated string");
        }

        private char unicodeEscape() {
            if (offset + 4 > input.length()) return fail("Incomplete Unicode escape");
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(input.charAt(offset++), 16);
                if (digit < 0) return fail("Invalid Unicode escape");
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private Object number() {
            int start = offset;
            consume('-');
            if (consume('0')) {
                if (offset < input.length() && Character.isDigit(input.charAt(offset))) fail("Leading zero in number");
            } else {
                digits(true);
            }
            if (consume('.')) digits(true);
            if (consume('e') || consume('E')) {
                if (!consume('+')) consume('-');
                digits(true);
            }
            if (start == offset) return fail("Invalid JSON value");
            try { return new BigDecimal(input.substring(start, offset)); }
            catch (NumberFormatException failure) { return fail("Invalid JSON number"); }
        }

        private void digits(boolean required) {
            int start = offset;
            while (offset < input.length() && input.charAt(offset) >= '0' && input.charAt(offset) <= '9') offset++;
            if (required && start == offset) fail("Expected a digit");
        }

        private Object literal(String token, Object value) {
            if (!input.startsWith(token, offset)) return fail("Invalid JSON value");
            offset += token.length();
            return value;
        }

        private void skipWhitespace() {
            while (offset < input.length()) {
                char c = input.charAt(offset);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') offset++;
                else return;
            }
        }

        private boolean consume(char expected) {
            if (offset < input.length() && input.charAt(offset) == expected) {
                offset++;
                return true;
            }
            return false;
        }

        private void require(char expected) {
            if (!consume(expected)) fail("Expected '" + expected + "'");
        }

        private <T> T fail(String message) {
            throw new IllegalArgumentException(message + " at character " + offset);
        }
    }
}
