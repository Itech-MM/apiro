package io.github.itech_mm.apiro.variables;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VariableScope {

    private static volatile Map<String, String> snapshot = Map.of();

    private VariableScope() {
    }

    public static void update(VariableResolver resolver) {
        if (resolver == null) {
            snapshot = Map.of();
            return;
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (String name : resolver.names()) {
            copy.put(name, resolver.value(name).orElse(""));
        }
        snapshot = Collections.unmodifiableMap(copy);
    }

    public static Map<String, String> current() {
        return snapshot;
    }
}