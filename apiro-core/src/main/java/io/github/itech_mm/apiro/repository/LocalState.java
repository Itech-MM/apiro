package io.github.itech_mm.apiro.repository;

import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record LocalState(String activeEnvironmentId, Map<String, Map<String, String>> secrets,
                         Map<String, LastRun> lastRuns, Map<String, RequestItem> drafts,
                         Map<String, ScenarioItem> scenarioDrafts) {

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
        Map<String, LastRun> runs = new LinkedHashMap<>();
        if (lastRuns != null) {
            lastRuns.forEach((requestId, run) -> {
                if (requestId != null && run != null) {
                    runs.put(requestId, run);
                }
            });
        }
        lastRuns = Collections.unmodifiableMap(runs);
        Map<String, RequestItem> kept = new LinkedHashMap<>();
        if (drafts != null) {
            drafts.forEach((requestId, draft) -> {
                if (requestId != null && draft != null) {
                    kept.put(requestId, draft);
                }
            });
        }
        drafts = Collections.unmodifiableMap(kept);
        Map<String, ScenarioItem> keptScenarios = new LinkedHashMap<>();
        if (scenarioDrafts != null) {
            scenarioDrafts.forEach((scenarioId, draft) -> {
                if (scenarioId != null && draft != null) {
                    keptScenarios.put(scenarioId, draft);
                }
            });
        }
        scenarioDrafts = Collections.unmodifiableMap(keptScenarios);
    }

    public static LocalState empty() {
        return new LocalState(null, null, null, null, null);
    }

    public String secret(String environmentId, String key) {
        return secrets.getOrDefault(environmentId, Map.of()).getOrDefault(key, "");
    }

    public LastRun lastRun(String requestId) {
        return lastRuns.get(requestId);
    }

    public LocalState withActiveEnvironment(String environmentId) {
        return new LocalState(environmentId, secrets, lastRuns, drafts, scenarioDrafts);
    }

    public LocalState withSecret(String environmentId, String key, String value) {
        Map<String, Map<String, String>> next = new LinkedHashMap<>(secrets);
        Map<String, String> values = new LinkedHashMap<>(next.getOrDefault(environmentId, Map.of()));
        values.put(key, value);
        next.put(environmentId, values);
        return new LocalState(activeEnvironmentId, next, lastRuns, drafts, scenarioDrafts);
    }

    public LocalState withLastRun(String requestId, LastRun run) {
        Map<String, LastRun> next = new LinkedHashMap<>(lastRuns);
        next.put(requestId, run);
        return new LocalState(activeEnvironmentId, secrets, next, drafts, scenarioDrafts);
    }

    public LocalState withDrafts(Map<String, RequestItem> next) {
        return new LocalState(activeEnvironmentId, secrets, lastRuns, next, scenarioDrafts);
    }

    public LocalState withScenarioDrafts(Map<String, ScenarioItem> next) {
        return new LocalState(activeEnvironmentId, secrets, lastRuns, drafts, next);
    }
}