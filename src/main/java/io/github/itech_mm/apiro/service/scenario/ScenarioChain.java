package io.github.itech_mm.apiro.service.scenario;

import io.github.itech_mm.apiro.model.Edge;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ScenarioChain {

    public record Result(List<Step> steps, String problem) {

        public Result {
            steps = steps == null ? List.of() : List.copyOf(steps);
            problem = problem == null ? "" : problem;
        }

        public boolean valid() {
            return problem.isEmpty();
        }
    }

    private ScenarioChain() {
    }

    public static Result order(ScenarioItem scenario) {
        List<Step> steps = scenario.steps();
        if (steps.isEmpty()) {
            return invalid("Scenario has no steps");
        }
        Map<String, Step> byId = new HashMap<>();
        for (Step step : steps) {
            if (byId.put(step.id(), step) != null) {
                return invalid("Two steps share the id " + step.id());
            }
        }
        if (scenario.edges().isEmpty()) {
            return new Result(steps, "");
        }
        Map<String, String> next = new HashMap<>();
        Set<String> hasIncoming = new HashSet<>();
        for (Edge edge : scenario.edges()) {
            if (!byId.containsKey(edge.from()) || !byId.containsKey(edge.to())) {
                return invalid("A connection points to a step that does not exist");
            }
            if (edge.from().equals(edge.to())) {
                return invalid("Step " + label(byId.get(edge.from())) + " is connected to itself");
            }
            if (next.put(edge.from(), edge.to()) != null) {
                return invalid("Step " + label(byId.get(edge.from()))
                        + " has more than one next step, branches are not supported yet");
            }
            if (!hasIncoming.add(edge.to())) {
                return invalid("More than one step leads to " + label(byId.get(edge.to()))
                        + ", merging is not supported yet");
            }
        }
        List<Step> heads = steps.stream().filter(step -> !hasIncoming.contains(step.id())).toList();
        if (heads.isEmpty()) {
            return invalid("The steps form a loop, loops are not supported yet");
        }
        if (heads.size() > 1) {
            return invalid("The steps are not connected in one chain");
        }
        List<Step> ordered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String current = heads.get(0).id();
        while (current != null && seen.add(current)) {
            ordered.add(byId.get(current));
            current = next.get(current);
        }
        if (ordered.size() != steps.size()) {
            return invalid("The steps are not connected in one chain");
        }
        return new Result(ordered, "");
    }

    private static Result invalid(String problem) {
        return new Result(List.of(), problem);
    }

    private static String label(Step step) {
        return "\"" + (step.name().isBlank() ? step.id() : step.name()) + "\"";
    }
}
