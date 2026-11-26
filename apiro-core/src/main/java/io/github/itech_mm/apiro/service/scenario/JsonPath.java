package io.github.itech_mm.apiro.service.scenario;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JsonPath {

    private final String expression;
    private final List<Object> segments;

    private JsonPath(String expression, List<Object> segments) {
        this.expression = expression;
        this.segments = segments;
    }

    public static JsonPath compile(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("JSON path is empty");
        }
        String text = expression.trim();
        int length = text.length();
        int i = text.charAt(0) == '$' ? 1 : 0;
        List<Object> segments = new ArrayList<>();
        if (i == 0 && text.charAt(0) != '.' && text.charAt(0) != '[') {
            int end = nameEnd(text, 0);
            segments.add(text.substring(0, end));
            i = end;
        }
        while (i < length) {
            char c = text.charAt(i);
            if (c == '.') {
                int start = i + 1;
                int end = nameEnd(text, start);
                if (end == start) {
                    throw invalid(expression, "missing name after '.'");
                }
                segments.add(text.substring(start, end));
                i = end;
            } else if (c == '[') {
                i = bracket(expression, text, i + 1, segments);
            } else {
                throw invalid(expression, "unexpected '" + c + "' at position " + i);
            }
        }
        return new JsonPath(expression.trim(), List.copyOf(segments));
    }

    public List<Object> segments() {
        return segments;
    }

    public String expression() {
        return expression;
    }

    public Optional<JsonElement> evaluate(JsonElement root) {
        JsonElement current = root;
        for (Object segment : segments) {
            if (current == null) {
                return Optional.empty();
            }
            if (segment instanceof String key) {
                if (!current.isJsonObject() || !current.getAsJsonObject().has(key)) {
                    return Optional.empty();
                }
                current = current.getAsJsonObject().get(key);
            } else {
                int index = (Integer) segment;
                if (!current.isJsonArray()) {
                    return Optional.empty();
                }
                JsonArray array = current.getAsJsonArray();
                if (index >= array.size()) {
                    return Optional.empty();
                }
                current = array.get(index);
            }
        }
        return Optional.ofNullable(current);
    }

    private static int bracket(String expression, String text, int from, List<Object> segments) {
        if (from >= text.length()) {
            throw invalid(expression, "unclosed '['");
        }
        char first = text.charAt(from);
        if (first == '\'' || first == '"') {
            int close = text.indexOf(first, from + 1);
            if (close < 0 || close + 1 >= text.length() || text.charAt(close + 1) != ']') {
                throw invalid(expression, "unclosed quoted key");
            }
            segments.add(text.substring(from + 1, close));
            return close + 2;
        }
        int close = text.indexOf(']', from);
        if (close < 0) {
            throw invalid(expression, "unclosed '['");
        }
        String index = text.substring(from, close).trim();
        try {
            int value = Integer.parseInt(index);
            if (value < 0) {
                throw invalid(expression, "negative index " + index);
            }
            segments.add(value);
        } catch (NumberFormatException e) {
            throw invalid(expression, "index '" + index + "' is not a whole number");
        }
        return close + 1;
    }

    private static int nameEnd(String text, int from) {
        int i = from;
        while (i < text.length() && text.charAt(i) != '.' && text.charAt(i) != '[') {
            i++;
        }
        return i;
    }

    private static IllegalArgumentException invalid(String expression, String reason) {
        return new IllegalArgumentException("Invalid JSON path " + expression + ": " + reason);
    }
}
