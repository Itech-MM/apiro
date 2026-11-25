package io.github.itech_mm.apiro.service.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RunRecord;
import io.github.itech_mm.apiro.model.RunStep;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RunRecordsTest {

    private static RunRecord run(long at, String state, long millis, RunStep... steps) {
        return new RunRecord(String.valueOf(at), at, "Checkout", "Local", state, "", millis, List.of(steps), "");
    }

    private static RunStep step(String state) {
        return new RunStep("s1", "Login", state, 200, 100, "", "");
    }

    @Test
    void recordKeepsStepsAndBuildsLog() {
        Step login = Step.of("r1", "Login");
        Step orders = Step.of("r2", "Orders");
        ScenarioItem scenario = ScenarioItem.of("Checkout");
        StepResult passed = new StepResult(login, StepResult.State.PASSED, "", 120, null, null, null);
        StepResult failed = new StepResult(orders, StepResult.State.FAILED, "boom", 80, null, null, null);
        ScenarioResult result = new ScenarioResult(scenario.id(), ScenarioResult.State.FAILED, "Orders failed",
                List.of(passed, failed), 900);

        RunRecord record = RunRecords.of(scenario, "Local", 5000, result, item -> "label " + item.name());

        assertEquals("5000", record.id());
        assertEquals("Checkout", record.scenarioName());
        assertEquals("Local", record.environmentName());
        assertEquals("FAILED", record.state());
        assertEquals(900, record.millis());
        assertEquals(2, record.steps().size());
        assertEquals(login.id(), record.steps().get(0).stepId());
        assertEquals("label Login", record.steps().get(0).name());
        assertEquals("FAILED", record.steps().get(1).state());
        assertEquals("boom", record.steps().get(1).message());
        assertEquals(1, record.count("PASSED"));
        assertTrue(record.log().contains("[PASSED] Login"), record.log());
        assertTrue(record.log().contains("Scenario failed in 900 ms"), record.log());
    }

    @Test
    void addPutsNewestFirstAndKeepsLimit() {
        List<RunRecord> runs = List.of();
        for (int i = 1; i <= 5; i++) {
            runs = RunRecords.add(runs, run(i, "PASSED", 10), 3);
        }
        assertEquals(List.of("5", "4", "3"), runs.stream().map(RunRecord::id).toList());
    }

    @Test
    void addReplacesARunWithTheSameId() {
        List<RunRecord> runs = RunRecords.add(List.of(run(1, "PASSED", 10), run(2, "PASSED", 10)),
                run(2, "FAILED", 20), 10);
        assertEquals(2, runs.size());
        assertEquals("FAILED", runs.get(0).state());
        assertEquals("1", runs.get(1).id());
    }

    @Test
    void labelShowsTimeStateAndCounts() {
        RunRecord record = run(0, "PASSED", 1200, step("PASSED"));
        assertEquals("1970-01-01 00:00:00  Passed  1/1  1.2 s", RunRecords.label(record, ZoneOffset.UTC));
    }

    @Test
    void labelCountsOnlyPassedSteps() {
        RunRecord record = run(0, "FAILED", 90, step("PASSED"), step("FAILED"), step("SKIPPED"));
        assertEquals("1970-01-01 00:00:00  Failed  1/3  90 ms", RunRecords.label(record, ZoneOffset.UTC));
    }

    @Test
    void briefLeavesOutTheTime() {
        RunRecord record = run(0, "FAILED", 90, step("PASSED"), step("FAILED"));
        assertEquals("Failed  1/2  90 ms", RunRecords.brief(record));
    }

    @Test
    void scenarioIdsIncludeScenariosInsideFolders() {
        ScenarioItem top = ScenarioItem.of("Top");
        ScenarioItem nested = ScenarioItem.of("Nested");
        Folder folder = Folder.of("Group", List.of(nested));
        Project project = new Project(null, 0, null, "Shop", null, null, null, null, null, null,
                List.of(top, folder));

        assertEquals(Set.of(top.id(), nested.id()), RunRecords.scenarioIds(project));
    }

    @Test
    void unknownStepStateFallsBackToPending() {
        assertEquals(StepResult.State.PENDING, RunRecords.stepState(step("")));
        assertEquals(StepResult.State.SKIPPED, RunRecords.stepState(step("SKIPPED")));
    }

    @Test
    void recordFillsDefaultsForMissingValues() {
        RunRecord record = new RunRecord(null, 7, null, null, null, null, -1, null, null);
        assertEquals("7", record.id());
        assertEquals(0, record.millis());
        assertTrue(record.steps().isEmpty());
        assertEquals("", record.log());
    }
}
