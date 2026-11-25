package io.github.itech_mm.apiro.service.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.Assertion;
import io.github.itech_mm.apiro.model.Extractor;
import io.github.itech_mm.apiro.model.Step;
import java.util.List;
import org.junit.jupiter.api.Test;

class StepSummaryTest {

    private static final Step STEP = Step.of("r1", "Login");

    private static final Assertion ASSERTION = new Assertion(Assertion.Source.STATUS, "", Assertion.Operator.EQUALS,
            "200");

    private static Assertions.Result assertion(boolean passed, String message) {
        return new Assertions.Result(ASSERTION, passed, "", message);
    }

    private static StepResult result(StepResult.State state, String message, long millis,
                                     List<Extractors.Result> extractions, List<Assertions.Result> assertions) {
        return new StepResult(STEP, state, message, millis, null, extractions, assertions);
    }

    @Test
    void passedStepShowsTimeAndLastAssertion() {
        String text = StepSummary.describe(result(StepResult.State.PASSED, "", 120, null,
                List.of(assertion(true, "status equals 200"), assertion(true, "$.id exists"))));
        assertEquals("Passed · 120 ms\nAssertions: 2 of 2 passed\nLast: $.id exists", text);
    }

    @Test
    void failureThatIsNotLastIsListedSeparately() {
        String text = StepSummary.describe(result(StepResult.State.FAILED, "", 1500, null,
                List.of(assertion(false, "Expected status to equal 200 but was 500"), assertion(true, "$.id exists"))));
        assertTrue(text.startsWith("Failed · 1.5 s"), text);
        assertTrue(text.contains("Assertions: 1 of 2 passed"), text);
        assertTrue(text.contains("Last: $.id exists"), text);
        assertTrue(text.contains("First failure: Expected status to equal 200 but was 500"), text);
    }

    @Test
    void lastFailureIsNotRepeated() {
        String text = StepSummary.describe(result(StepResult.State.FAILED, "", 0, null,
                List.of(assertion(true, "ok"), assertion(false, "bad"))));
        assertTrue(text.contains("Last: bad"), text);
        assertFalse(text.contains("First failure"), text);
    }

    @Test
    void messageAndExtractionFailureAreShown() {
        Extractors.Result failed = new Extractors.Result(new Extractor("token", Extractor.Source.JSON_PATH, "$.t"),
                false, "", "JSON path $.t not found");
        String text = StepSummary.describe(result(StepResult.State.FAILED, "Connection refused", 40,
                List.of(failed), List.of()));
        assertEquals("Failed · 40 ms\nConnection refused\nExtract token failed: JSON path $.t not found", text);
    }

    @Test
    void waitingStepHasOnlyAHeadline() {
        assertEquals("Waiting", StepSummary.describe(result(StepResult.State.PENDING, "", 0, null, null)));
    }

    @Test
    void longMessagesAreShortenedToOneLine() {
        String text = StepSummary.describe(result(StepResult.State.FAILED, "x\n".repeat(400), 0, null, null));
        String second = text.split("\n")[1];
        assertTrue(second.length() < 320, second);
        assertTrue(second.endsWith("..."), second);
    }

    @Test
    void durationSwitchesToSeconds() {
        assertEquals("999 ms", StepSummary.duration(999));
        assertEquals("1.0 s", StepSummary.duration(1000));
        assertEquals("12.3 s", StepSummary.duration(12345));
    }
}
