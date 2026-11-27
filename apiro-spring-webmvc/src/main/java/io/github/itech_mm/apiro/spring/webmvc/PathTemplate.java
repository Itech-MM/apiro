package io.github.itech_mm.apiro.spring.webmvc;

import java.util.LinkedHashMap;
import java.util.Map;

final class PathTemplate {
    private final String path;
    private final Map<String, String> variables;

    private PathTemplate(String path, Map<String, String> variables) {
        this.path = path;
        this.variables = variables;
    }

    String path() {
        return path;
    }

    Map<String, String> variables() {
        return variables;
    }

    static PathTemplate parse(String pattern) {
        StringBuilder path = new StringBuilder();
        Map<String, String> variables = new LinkedHashMap<>();
        if (!pattern.startsWith("/")) {
            path.append('/');
        }
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            int end = c == '{' ? closing(pattern, i) : -1;
            if (end < 0) {
                path.append(c);
                i++;
                continue;
            }
            String inner = pattern.substring(i + 1, end);
            int colon = inner.indexOf(':');
            String name = colon < 0 ? inner : inner.substring(0, colon);
            if (name.startsWith("*")) {
                name = name.substring(1);
            }
            variables.put(name, colon < 0 ? null : inner.substring(colon + 1));
            path.append('{').append(name).append('}');
            i = end + 1;
        }
        return new PathTemplate(path.toString(), variables);
    }

    private static int closing(String pattern, int open) {
        int depth = 0;
        for (int i = open; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }
}
