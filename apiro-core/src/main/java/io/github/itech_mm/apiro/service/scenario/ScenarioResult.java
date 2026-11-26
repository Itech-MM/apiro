package io.github.itech_mm.apiro.service.scenario;

import java.util.List;

public record ScenarioResult(String scenarioId, State state, String message, List<StepResult> steps, long millis) {

    public enum State {
        PASSED, FAILED, CANCELLED, INVALID
    }

    public ScenarioResult {
        message = message == null ? "" : message;
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public long count(StepResult.State wanted) {
        return steps.stream().filter(step -> step.state() == wanted).count();
    }
}
