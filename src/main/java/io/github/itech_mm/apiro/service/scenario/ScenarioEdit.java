package io.github.itech_mm.apiro.service.scenario;

import io.github.itech_mm.apiro.model.Edge;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.OnFailure;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.model.Variable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ScenarioEdit {

    public record RequestRef(String id, String label, HttpMethod method) {
    }

    private static final Pattern SENSITIVE = Pattern.compile("token|secret|password|passwd|key|auth|cookie|session",
            Pattern.CASE_INSENSITIVE);

    private ScenarioEdit() {
    }

    public static ScenarioItem withAppended(ScenarioItem scenario, String requestId, String name) {
        List<Step> steps = new ArrayList<>(scenario.steps());
        steps.add(Step.of(requestId, name));
        return rebuilt(scenario, steps);
    }

    public static ScenarioItem withoutStep(ScenarioItem scenario, String stepId) {
        List<Step> steps = new ArrayList<>(scenario.steps());
        if (!steps.removeIf(step -> step.id().equals(stepId))) {
            return scenario;
        }
        return rebuilt(scenario, steps);
    }

    public static ScenarioItem withMovedStep(ScenarioItem scenario, String stepId, int targetIndex) {
        List<Step> steps = new ArrayList<>(scenario.steps());
        int from = indexOf(steps, stepId);
        if (from < 0) {
            return scenario;
        }
        int to = Math.max(0, Math.min(targetIndex, steps.size() - 1));
        if (from == to) {
            return scenario;
        }
        steps.add(to, steps.remove(from));
        return rebuilt(scenario, steps);
    }

    public static ScenarioItem withStep(ScenarioItem scenario, Step updated) {
        List<Step> steps = new ArrayList<>(scenario.steps());
        int index = indexOf(steps, updated.id());
        if (index < 0 || steps.get(index).equals(updated)) {
            return scenario;
        }
        steps.set(index, updated);
        return new ScenarioItem(scenario.id(), scenario.name(), scenario.variables(), steps, scenario.edges(),
                scenario.onFailure());
    }

    public static ScenarioItem withOnFailure(ScenarioItem scenario, OnFailure policy) {
        if (scenario.onFailure() == policy) {
            return scenario;
        }
        return new ScenarioItem(scenario.id(), scenario.name(), scenario.variables(), scenario.steps(),
                scenario.edges(), policy);
    }

    public static ScenarioItem withVariables(ScenarioItem scenario, List<Variable> variables) {
        ScenarioItem next = new ScenarioItem(scenario.id(), scenario.name(), variables, scenario.steps(),
                scenario.edges(), scenario.onFailure());
        return next.equals(scenario) ? scenario : next;
    }

    public static List<RequestRef> requests(Project project) {
        List<RequestRef> found = new ArrayList<>();
        collect(project.items(), "", found);
        return found;
    }

    public static String maskedValue(String variable, String value) {
        if (value == null) {
            return "";
        }
        if (SENSITIVE.matcher(variable == null ? "" : variable.toLowerCase(Locale.ROOT)).find()) {
            return "••••";
        }
        return value.length() > 80 ? value.substring(0, 80) + "…" : value;
    }

    private static void collect(List<ProjectItem> items, String prefix, List<RequestRef> out) {
        for (ProjectItem item : items) {
            if (item instanceof RequestItem request) {
                out.add(new RequestRef(request.id(), prefix + request.name(), request.method()));
            } else if (item instanceof Folder folder) {
                collect(folder.items(), prefix + folder.name() + " / ", out);
            }
        }
    }

    private static ScenarioItem rebuilt(ScenarioItem scenario, List<Step> steps) {
        List<Edge> edges = new ArrayList<>();
        if (!scenario.edges().isEmpty()) {
            for (int i = 1; i < steps.size(); i++) {
                edges.add(new Edge(steps.get(i - 1).id(), steps.get(i).id()));
            }
        }
        return new ScenarioItem(scenario.id(), scenario.name(), scenario.variables(), steps, edges,
                scenario.onFailure());
    }

    private static int indexOf(List<Step> steps, String stepId) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).id().equals(stepId)) {
                return i;
            }
        }
        return -1;
    }
}
