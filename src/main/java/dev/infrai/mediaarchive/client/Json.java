package dev.infrai.mediaarchive.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {}

    public static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return quote(text);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                out.append(quote(String.valueOf(entry.getKey()))).append(':').append(write(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder out = new StringBuilder("[");
            boolean first = true;
            for (Object item : values) {
                if (!first) out.append(',');
                first = false;
                out.append(write(item));
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
    }

    public static Object parse(String source) {
        Parser parser = new Parser(source);
        Object value = parser.value();
        parser.space();
        if (!parser.end()) throw parser.failure("Trailing data");
        return value;
    }

    public static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected JSON object");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) { this.source = source; }
        private boolean end() { return index == source.length(); }
        private void space() { while (!end() && Character.isWhitespace(source.charAt(index))) index++; }

        private Object value() {
            space();
            if (end()) throw failure("Expected value");
            return switch (source.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            index++;
            Map<String, Object> result = new LinkedHashMap<>();
            space();
            if (take('}')) return result;
            do {
                space();
                String key = string();
                space();
                require(':');
                result.put(key, value());
                space();
            } while (take(','));
            require('}');
            return result;
        }

        private List<Object> array() {
            index++;
            List<Object> result = new ArrayList<>();
            space();
            if (take(']')) return result;
            do {
                result.add(value());
                space();
            } while (take(','));
            require(']');
            return result;
        }

        private String string() {
            require('"');
            StringBuilder out = new StringBuilder();
            while (!end()) {
                char c = source.charAt(index++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (end()) throw failure("Incomplete escape");
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (index + 4 > source.length()) throw failure("Incomplete unicode escape");
                        out.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw failure("Invalid escape");
                }
            }
            throw failure("Unclosed string");
        }

        private Object number() {
            int start = index;
            while (!end() && "-+0123456789.eE".indexOf(source.charAt(index)) >= 0) index++;
            if (start == index) throw failure("Expected number");
            String token = source.substring(start, index);
            try {
                return token.contains(".") || token.contains("e") || token.contains("E")
                        ? Double.parseDouble(token) : Long.parseLong(token);
            } catch (NumberFormatException error) {
                throw failure("Invalid number");
            }
        }

        private Object literal(String token, Object value) {
            if (!source.startsWith(token, index)) throw failure("Invalid literal");
            index += token.length();
            return value;
        }

        private boolean take(char expected) {
            if (!end() && source.charAt(index) == expected) { index++; return true; }
            return false;
        }

        private void require(char expected) {
            if (!take(expected)) throw failure("Expected '" + expected + "'");
        }

        private IllegalArgumentException failure(String message) {
            return new IllegalArgumentException(message + " at character " + index);
        }
    }
}
