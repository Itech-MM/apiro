package io.github.itech_mm.apiro.service.scenario;

import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.service.http.ExchangeResult;
import java.util.List;

public record StepResult(Step step, State state, String message, long millis, ExchangeResult exchange,
                         List<Extractors.Result> extractions, List<Assertions.Result> assertions,
                         BranchDecision branch) {

    public enum State {
        PENDING, RUNNING, PASSED, FAILED, SKIPPED, CANCELLED
    }

    public record BranchDecision(String fromStepId, String edgeId, String toStepId, String targetName,
                                 String condition, String reason) {

        public BranchDecision {
            fromStepId = fromStepId == null ? "" : fromStepId;
            edgeId = edgeId == null ? "" : edgeId;
            toStepId = toStepId == null ? "" : toStepId;
            targetName = targetName == null ? "" : targetName;
            condition = condition == null ? "" : condition;
            reason = reason == null ? "" : reason;
        }

        public boolean taken() {
            return !toStepId.isBlank();
        }
    }

    public StepResult {
        message = message == null ? "" : message;
        extractions = extractions == null ? List.of() : List.copyOf(extractions);
        assertions = assertions == null ? List.of() : List.copyOf(assertions);
    }

    public StepResult(Step step, State state, String message, long millis, ExchangeResult exchange,
                      List<Extractors.Result> extractions, List<Assertions.Result> assertions) {
        this(step, state, message, millis, exchange, extractions, assertions, null);
    }

    public static StepResult of(Step step, State state, String message) {
        return new StepResult(step, state, message, 0, null, null, null, null);
    }

    public int status() {
        return exchange == null ? 0 : exchange.response().status();
    }
}
