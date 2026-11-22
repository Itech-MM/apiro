package io.github.itech_mm.apiro.storage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record LocalState(String activeEnvironmentId, Map<String, Map<String, String>> secrets) {

    public LocalState {
        activeEnvironmentId = activeEnvironmentId == null ? "" : activeEnvironmentId;
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        if (secrets != null) {
            secrets.forEach((environmentId, values) -> copy.put(environmentId,
                    values == null
                            ? Map.of()
                            : Collections.unmodifiableMap(new LinkedHashMap<>(values))));
        }
        secrets = Collections.unmodifiableMap(copy);
    }

    public static LocalState empty() {
        return new LocalState(null, null);
    }

    public String secret(String environmentId, String key) {
        return secrets.getOrDefault(environmentId, Map.of()).getOrDefault(key, "");
    }

    public LocalState withActiveEnvironment(String environmentId) {
        return new LocalState(environmentId, secrets);
    }

    public LocalState withSecret(String environmentId, String key, String value) {
        Map<String, Map<String, String>> next = new LinkedHashMap<>(secrets);
        Map<String, String> values = new LinkedHashMap<>(next.getOrDefault(environmentId, Map.of()));
        values.put(key, value);
        next.put(environmentId, values);
        return new LocalState(activeEnvironmentId, next);
    }
}