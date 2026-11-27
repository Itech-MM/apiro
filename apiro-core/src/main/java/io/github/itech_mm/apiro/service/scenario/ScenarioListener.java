package io.github.itech_mm.apiro.service.scenario;

import io.github.itech_mm.apiro.model.Step;
import java.util.List;

public interface ScenarioListener {

    ScenarioListener NONE = new ScenarioListener() {
    };

    default void onStarted(List<Step> steps) {
    }

    default void onStepStarted(Step step) {
    }

    default void onStepFinished(StepResult result) {
    }

    default void onBranchTaken(StepResult.BranchDecision decision) {
    }

    default void onFinished(ScenarioResult result) {
    }
}
