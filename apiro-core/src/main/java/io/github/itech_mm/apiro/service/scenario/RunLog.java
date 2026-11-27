package io.github.itech_mm.apiro.service.scenario;

import io.github.itech_mm.apiro.model.Step;
import java.util.ArrayList;
import java.util.List;

public final class RunLog {

    private RunLog() {
    }

    public static String label(Step step) {
        return step.name().isBlank() ? step.requestId() : step.name();
    }

    public static List<String> stepLines(StepResult result, String label) {
        List<String> lines = new ArrayList<>();
        StringBuilder head = new StringBuilder("[").append(result.state()).append("] ").append(label);
        if (result.status() > 0) {
            head.append("  ").append(result.status());
        }
        if (result.millis() > 0) {
            head.append("  ").append(result.millis()).append(" ms");
        }
        lines.add(head.toString());
        if (!result.message().isBlank() && result.state() != StepResult.State.PASSED) {
            lines.add("    " + result.message());
        }
        for (Extractors.Result extraction : result.extractions()) {
            String name = extraction.extractor().variable();
            if (extraction.success()) {
                lines.add("    extract " + name + " = " + ScenarioEdit.maskedValue(name, extraction.value()));
            } else {
                lines.add("    extract " + name + " failed: " + extraction.error());
            }
        }
        for (Assertions.Result assertion : result.assertions()) {
            lines.add("    " + (assertion.passed() ? "pass " : "fail ") + assertion.message());
        }
        if (result.branch() != null) {
            StepResult.BranchDecision branch = result.branch();
            if (branch.taken()) {
                String suffix = branch.condition().isBlank() ? "" : " (" + branch.condition() + ")";
                lines.add("    branch -> " + branch.targetName() + suffix);
            } else if (!branch.reason().isBlank()) {
                lines.add("    branch: " + branch.reason());
            }
        }
        return lines;
    }

    public static String summary(ScenarioResult result) {
        StringBuilder text = new StringBuilder("Scenario ").append(result.state().name().toLowerCase())
                .append(" in ").append(result.millis()).append(" ms");
        if (!result.steps().isEmpty()) {
            text.append(": ").append(result.count(StepResult.State.PASSED)).append(" passed, ")
                    .append(result.count(StepResult.State.FAILED)).append(" failed, ")
                    .append(result.count(StepResult.State.SKIPPED)).append(" skipped");
        }
        if (!result.message().isBlank()) {
            text.append(" (").append(result.message()).append(")");
        }
        return text.toString();
    }
}
