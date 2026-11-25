package io.github.itech_mm.apiro.service.scenario;

import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RunRecord;
import io.github.itech_mm.apiro.model.RunStep;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

public final class RunRecords {

    public static final int LIMIT = 30;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private RunRecords() {
    }

    public static RunRecord of(ScenarioItem scenario, String environmentName, long startedAt, ScenarioResult result,
                               Function<Step, String> labels) {
        List<RunStep> steps = new ArrayList<>();
        StringBuilder log = new StringBuilder();
        for (StepResult step : result.steps()) {
            steps.add(new RunStep(step.step().id(), labels.apply(step.step()), step.state().name(), step.status(),
                    step.millis(), step.message(), StepSummary.describe(step)));
            RunLog.stepLines(step, RunLog.label(step.step())).forEach(line -> log.append(line).append('\n'));
        }
        log.append(RunLog.summary(result)).append('\n');
        return new RunRecord(String.valueOf(startedAt), startedAt, scenario.name(), environmentName,
                result.state().name(), result.message(), result.millis(), steps, log.toString());
    }

    public static List<RunRecord> add(List<RunRecord> existing, RunRecord next, int limit) {
        List<RunRecord> merged = new ArrayList<>();
        merged.add(next);
        for (RunRecord run : existing) {
            if (!run.id().equals(next.id())) {
                merged.add(run);
            }
        }
        int keep = Math.max(1, limit);
        return List.copyOf(merged.size() > keep ? merged.subList(0, keep) : merged);
    }

    public static String when(RunRecord run, ZoneId zone) {
        return STAMP.withZone(zone).format(Instant.ofEpochMilli(run.startedAt()));
    }

    public static String label(RunRecord run, ZoneId zone) {
        return when(run, zone) + "  " + brief(run);
    }

    public static String brief(RunRecord run) {
        return word(run.state()) + "  " + run.count("PASSED") + "/" + run.steps().size() + "  "
                + StepSummary.duration(run.millis());
    }

    public static Set<String> scenarioIds(Project project) {
        Set<String> ids = new LinkedHashSet<>();
        collectScenarios(project.items(), ids);
        return ids;
    }

    public static StepResult.State stepState(RunStep step) {
        try {
            return StepResult.State.valueOf(step.state());
        } catch (IllegalArgumentException error) {
            return StepResult.State.PENDING;
        }
    }

    private static void collectScenarios(List<ProjectItem> items, Set<String> ids) {
        for (ProjectItem item : items) {
            if (item instanceof ScenarioItem scenario) {
                ids.add(scenario.id());
            } else if (item instanceof Folder folder) {
                collectScenarios(folder.items(), ids);
            }
        }
    }

    private static String word(String state) {
        if (state.isEmpty()) {
            return "";
        }
        String lower = state.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
