package io.github.itech_mm.apiro.model;

import java.util.List;
import java.util.Objects;

public record RunRecord(String id, long startedAt, String scenarioName, String environmentName, String state,
                        String message, long millis, List<RunStep> steps, String log) {

    public RunRecord {
        startedAt = Math.max(0, startedAt);
        id = id == null || id.isBlank() ? String.valueOf(startedAt) : id;
        scenarioName = scenarioName == null ? "" : scenarioName;
        environmentName = environmentName == null ? "" : environmentName;
        state = state == null ? "" : state;
        message = message == null ? "" : message;
        millis = Math.max(0, millis);
        steps = steps == null ? List.of() : steps.stream().filter(Objects::nonNull).toList();
        log = log == null ? "" : log;
    }

    public long count(String stepState) {
        return steps.stream().filter(step -> step.state().equals(stepState)).count();
    }
}
