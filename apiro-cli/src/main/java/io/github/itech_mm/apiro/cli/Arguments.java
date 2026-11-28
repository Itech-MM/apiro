package io.github.itech_mm.apiro.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Arguments {

    private final List<String> positionals;
    private final Map<String, List<String>> values;
    private final Set<String> flags;

    private Arguments(List<String> positionals, Map<String, List<String>> values, Set<String> flags) {
        this.positionals = positionals;
        this.values = values;
        this.flags = flags;
    }

    static Arguments parse(List<String> raw, Set<String> valueOptions, Set<String> flagOptions) {
        List<String> positionals = new ArrayList<>();
        Map<String, List<String>> values = new LinkedHashMap<>();
        Set<String> flags = new LinkedHashSet<>();
        boolean optionsEnded = false;
        for (int i = 0; i < raw.size(); i++) {
            String token = raw.get(i);
            if (optionsEnded || !token.startsWith("-") || token.equals("-")) {
                positionals.add(token);
                continue;
            }
            if (token.equals("--")) {
                optionsEnded = true;
                continue;
            }
            String name = token;
            String inline = null;
            int equals = token.indexOf('=');
            if (token.startsWith("--") && equals > 0) {
                name = token.substring(0, equals);
                inline = token.substring(equals + 1);
            }
            if (flagOptions.contains(name)) {
                if (inline != null) {
                    throw new CliException("Option " + name + " does not take a value");
                }
                flags.add(name);
            } else if (valueOptions.contains(name)) {
                String value = inline;
                if (value == null) {
                    if (i + 1 >= raw.size()) {
                        throw new CliException("Option " + name + " needs a value");
                    }
                    value = raw.get(++i);
                }
                values.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
            } else {
                throw new CliException("Unknown option " + name);
            }
        }
        return new Arguments(List.copyOf(positionals), values, flags);
    }

    List<String> positionals() {
        return positionals;
    }

    String value(String name) {
        List<String> found = values.get(name);
        return found == null || found.isEmpty() ? null : found.get(found.size() - 1);
    }

    List<String> values(String name) {
        List<String> found = values.get(name);
        return found == null ? List.of() : List.copyOf(found);
    }

    boolean flag(String name) {
        return flags.contains(name);
    }
}
