package io.github.itech_mm.apiro.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MiniJson {
    static final class Num {
        private final String literal;

        Num(String literal) {
            this.literal = literal;
        }

        String literal() {
            return literal;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Num && ((Num) o).literal.equals(literal);
        }

        @Override
        public int hashCode() {
            return literal.hashCode();
        }

        @Override
        public String toString() {
            return literal;
        }
    }

    private static final Pattern NUMBER = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");

    private final String text;
    private int pos;

    private MiniJson(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        MiniJson parser = new MiniJson(text);
        parser.skipWhitespace();
        Object value = parser.value();
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw parser.error("unexpected content after the document");
        }
        return value;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at offset " + pos);
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private boolean peek(char expected) {
        return pos < text.length() && text.charAt(pos) == expected;
    }

    private void expect(char expected) {
        if (!peek(expected)) {
            throw error("expected '" + expected + "'");
        }
        pos++;
    }

    private void word(String word) {
        if (!text.startsWith(word, pos)) {
            throw error("unexpected character");
        }
        pos += word.length();
    }

    private Object value() {
        if (pos >= text.length()) {
            throw error("unexpected end of input");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                word("true");
                return Boolean.TRUE;
            case 'f':
                word("false");
                return Boolean.FALSE;
            case 'n':
                throw error("null is not allowed: canonical manifests omit absent fields");
            default:
                return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipWhitespace();
        if (peek('}')) {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (!peek('"')) {
                throw error("expected a string key");
            }
            String key = string();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            Object value = value();
            if (map.containsKey(key)) {
                throw error("duplicate key '" + key + "'");
            }
            map.put(key, value);
            skipWhitespace();
            if (peek(',')) {
                pos++;
                continue;
            }
            expect('}');
            return map;
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipWhitespace();
        if (peek(']')) {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(value());
            skipWhitespace();
            if (peek(',')) {
                pos++;
                continue;
            }
            expect(']');
            return list;
        }
    }

    private String string() {
        pos++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw error("unterminated string");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c < 0x20) {
                throw error("control character in string");
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw error("unterminated escape");
            }
            char escape = text.charAt(pos++);
            switch (escape) {
                case '"':
                    sb.append('"');
                    break;
                case '\\':
                    sb.append('\\');
                    break;
                case '/':
                    sb.append('/');
                    break;
                case 'b':
                    sb.append('\b');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'u':
                    if (pos + 4 > text.length()) {
                        throw error("short unicode escape");
                    }
                    try {
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException e) {
                        throw error("bad unicode escape");
                    }
                    pos += 4;
                    break;
                default:
                    throw error("bad escape");
            }
        }
    }

    private Num number() {
        Matcher matcher = NUMBER.matcher(text);
        matcher.region(pos, text.length());
        if (!matcher.lookingAt()) {
            throw error("unexpected character");
        }
        pos = matcher.end();
        return new Num(matcher.group());
    }
}
