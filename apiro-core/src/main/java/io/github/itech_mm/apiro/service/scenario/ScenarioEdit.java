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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ScenarioEdit {

    public record RequestRef(String id, String label, HttpMethod method) {
    }

    private static final Pattern SENSITIVE = Pattern.compile("token|secret|password|passwd|key|auth|cookie|session",
            Pattern.CASE_INSENSITIVE);

    private ScenarioEdit() {
    }

    public static ScenarioItem withAppended(ScenarioItem scenario, String requestId, String name) {
        return withInserted(scenario, requestId, name, scenario.steps().size());
    }

    public static ScenarioItem withInserted(ScenarioItem scenario, String requestId, String name, int index) {
        List<Step> steps = new ArrayList<>(scenario.steps());
        steps.add(Math.max(0, Math.min(index, steps.size())), Step.of(requestId, name));
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

    public static ScenarioItem withMaterializedEdges(ScenarioItem scenario) {
        if (!scenario.edges().isEmpty() || scenario.steps().size() < 2) {
            return scenario;
        }
        List<Edge> edges = new ArrayList<>();
        for (int i = 1; i < scenario.steps().size(); i++) {
            edges.add(Edge.of(scenario.steps().get(i - 1).id(), scenario.steps().get(i).id()));
        }
        return new ScenarioItem(scenario.id(), scenario.name(), scenario.variables(), scenario.steps(), edges,
                scenario.onFailure());
    }

    public static ScenarioItem withEdge(ScenarioItem scenario, Edge edge) {
        Edge normalized = edge.id().isBlank()
                ? new Edge(UUID.randomUUID().toString(), edge.from(), edge.to(), edge.condition(), edge.label())
                : edge;
        List<Edge> edges = new ArrayList<>(scenario.edges());
        for (int i = 0; i < edges.size(); i++) {
            if (edges.get(i).id().equals(normalized.id())) {
                if (edges.get(i).equals(normalized)) {
                    return scenario;
                }
                edges.set(i, normalized);
                return new ScenarioItem(scenario.id(), scenario.name(), scenario.variables(), scenario.steps(), edges,
                        scenario.onFailure());
            }
        }
        edges.add(normalized);
        return new ScenarioItem(scenario.id(), scenario.name(), scenario.variables(), scenario.steps(), edges,
                scenario.onFailure());
    }

    public static ScenarioItem withoutEdge(ScenarioItem scenario, String edgeId) {
        if (edgeId == null || edgeId.isBlank()) {
            return scenario;
        }
        List<Edge> edges = new ArrayList<>(scenario.edges());
        if (!edges.removeIf(edge -> edge.id().equals(edgeId))) {
            return scenario;
        }
        return new ScenarioItem(scenario.id(), scenario.name(), scenario.variables(), scenario.steps(), edges,
                scenario.onFailure());
    }

    public static ScenarioItem withMovedEdge(ScenarioItem scenario, String fromStepId, String edgeId,
                                             int targetRelativeIndex) {
        List<Edge> all = new ArrayList<>(scenario.edges());
        List<Integer> positions = new ArrayList<>();
        List<Edge> outgoing = new ArrayList<>();
        int fromIndex = -1;
        for (int i = 0; i < all.size(); i++) {
            Edge edge = all.get(i);
            if (edge.from().equals(fromStepId)) {
                if (edge.id().equals(edgeId)) {
                    fromIndex = outgoing.size();
                }
                positions.add(i);
                outgoing.add(edge);
            }
        }
        if (fromIndex < 0) {
            return scenario;
        }
        int toIndex = Math.max(0, Math.min(targetRelativeIndex, outgoing.size() - 1));
        if (fromIndex == toIndex) {
            return scenario;
        }
        outgoing.add(toIndex, outgoing.remove(fromIndex));
        for (int i = 0; i < positions.size(); i++) {
            all.set(positions.get(i), outgoing.get(i));
        }
        return new ScenarioItem(scenario.id(), scenario.name(), scenario.variables(), scenario.steps(), all,
                scenario.onFailure());
    }

    public static ScenarioItem withContent(ScenarioItem scenario, ScenarioItem snapshot) {
        return new ScenarioItem(scenario.id(), scenario.name(), snapshot.variables(), snapshot.steps(),
                snapshot.edges(), snapshot.onFailure());
    }

    public static boolean sameContent(ScenarioItem first, ScenarioItem second) {
        return first.variables().equals(second.variables()) && first.steps().equals(second.steps())
                && first.edges().equals(second.edges()) && first.onFailure() == second.onFailure();
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
            if (isSimpleLinearEdges(scenario)) {
                for (int i = 1; i < steps.size(); i++) {
                    edges.add(new Edge(steps.get(i - 1).id(), steps.get(i).id()));
                }
            } else {
                Set<String> validIds = new HashSet<>();
                for (Step step : steps) {
                    validIds.add(step.id());
                }
                for (Edge edge : scenario.edges()) {
                    if (validIds.contains(edge.from()) && validIds.contains(edge.to())) {
                        edges.add(edge);
                    }
                }
            }
        }
        return new ScenarioItem(scenario.id(), scenario.name(), scenario.variables(), steps, edges,
                scenario.onFailure());
    }

    private static boolean isSimpleLinearEdges(ScenarioItem scenario) {
        Map<String, Integer> outgoing = new HashMap<>();
        Map<String, Integer> incoming = new HashMap<>();
        for (Edge edge : scenario.edges()) {
            if (edge.isConditional() || !edge.label().isBlank() || !edge.id().isBlank()) {
                return false;
            }
            if (outgoing.merge(edge.from(), 1, Integer::sum) > 1) {
                return false;
            }
            if (incoming.merge(edge.to(), 1, Integer::sum) > 1) {
                return false;
            }
        }
        return true;
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
