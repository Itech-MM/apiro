package io.github.itech_mm.apiro.ui;

import java.util.Optional;

public final class JsonPretty {

    private static final String INDENT = "  ";

    private JsonPretty() {
    }

    public static Optional<String> format(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        StringBuilder out = new StringBuilder(text.length() + text.length() / 4);
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    inString = true;
                    out.append(c);
                }
                case '{', '[' -> {
                    int next = nextSignificant(text, i + 1);
                    char close = c == '{' ? '}' : ']';
                    if (next < length && text.charAt(next) == close) {
                        out.append(c).append(close);
                        i = next;
                    } else {
                        out.append(c);
                        depth++;
                        newline(out, depth);
                    }
                }
                case '}', ']' -> {
                    depth--;
                    if (depth < 0) {
                        return Optional.empty();
                    }
                    newline(out, depth);
                    out.append(c);
                }
                case ',' -> {
                    out.append(c);
                    newline(out, depth);
                }
                case ':' -> out.append(": ");
                case ' ', '\t', '\r', '\n' -> {
                }
                default -> out.append(c);
            }
        }
        if (inString || depth != 0) {
            return Optional.empty();
        }
        return Optional.of(out.toString());
    }

    private static int nextSignificant(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static void newline(StringBuilder out, int depth) {
        out.append('\n');
        for (int i = 0; i < depth; i++) {
            out.append(INDENT);
        }
    }
}