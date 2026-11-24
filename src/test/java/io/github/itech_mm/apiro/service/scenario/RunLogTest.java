package io.github.itech_mm.apiro.service.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.Extractor;
import io.github.itech_mm.apiro.model.Step;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunLogTest {

    @Test
    void failedStepShowsItsMessageAndMasksTokens() {
        Step step = Step.of("r1", "Login");
        StepResult result = new StepResult(step, StepResult.State.FAILED, "Expected 200", 42, null,
                List.of(new Extractors.Result(new Extractor("accessToken", Extractor.Source.JSON_PATH, "$.t"), true,
                        "secret-value", ""),
                        new Extractors.Result(new Extractor("id", Extractor.Source.JSON_PATH, "$.id"), false, "",
                                "path not found")),
                null);

        List<String> lines = RunLog.stepLines(result, "Login");

        assertEquals("[FAILED] Login  42 ms", lines.get(0));
        assertEquals("    Expected 200", lines.get(1));
        assertEquals("    extract accessToken = ••••", lines.get(2));
        assertEquals("    extract id failed: path not found", lines.get(3));
    }

    @Test
    void summaryCountsStates() {
        Step step = Step.of("r1", "A");
        ScenarioResult result = new ScenarioResult("s", ScenarioResult.State.FAILED, "A failed",
                List.of(StepResult.of(step, StepResult.State.PASSED, ""),
                        StepResult.of(step, StepResult.State.FAILED, "x"),
                        StepResult.of(step, StepResult.State.SKIPPED, "y")),
                900);

        String text = RunLog.summary(result);

        assertTrue(text.startsWith("Scenario failed in 900 ms: 1 passed, 1 failed, 1 skipped"));
        assertTrue(text.endsWith("(A failed)"));
    }

    @Test
    void labelFallsBackToRequestId() {
        assertEquals("r9", RunLog.label(Step.of("r9", "")));
        assertEquals("Named", RunLog.label(Step.of("r9", "Named")));
    }
}
