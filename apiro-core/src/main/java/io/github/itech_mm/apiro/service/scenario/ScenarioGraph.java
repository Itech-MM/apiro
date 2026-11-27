package io.github.itech_mm.apiro.service.scenario;

import io.github.itech_mm.apiro.model.Edge;
import io.github.itech_mm.apiro.model.EdgeCondition;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ScenarioGraph {

    public record Plan(List<Step> steps, Map<String, Step> byId, Map<String, List<Edge>> outgoing,
                       Map<String, List<Edge>> incoming, Map<String, Set<String>> ancestors, String problem) {

        public Plan {
            steps = steps == null ? List.of() : List.copyOf(steps);
            byId = byId == null ? Map.of() : Map.copyOf(byId);
            outgoing = outgoing == null ? Map.of() : copyEdgeMap(outgoing);
            incoming = incoming == null ? Map.of() : copyEdgeMap(incoming);
            ancestors = ancestors == null ? Map.of() : copySetMap(ancestors);
            problem = problem == null ? "" : problem;
        }

        public boolean valid() {
            return problem.isEmpty();
        }

        public Step root() {
            return steps.isEmpty() ? null : steps.get(0);
        }

        public List<Edge> outgoingOf(String stepId) {
            return outgoing.getOrDefault(stepId, List.of());
        }

        public List<Edge> incomingOf(String stepId) {
            return incoming.getOrDefault(stepId, List.of());
        }

        public Set<String> ancestorsOf(String stepId) {
            return ancestors.getOrDefault(stepId, Set.of());
        }

        public Set<String> reachableAfter(String stepId) {
            Set<String> visited = new LinkedHashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            for (Edge edge : outgoingOf(stepId)) {
                if (visited.add(edge.to())) {
                    queue.add(edge.to());
                }
            }
            while (!queue.isEmpty()) {
                String current = queue.poll();
                for (Edge edge : outgoingOf(current)) {
                    if (visited.add(edge.to())) {
                        queue.add(edge.to());
                    }
                }
            }
            return visited;
        }

        public boolean isBranched() {
            for (List<Edge> list : outgoing.values()) {
                if (list.size() > 1) {
                    return true;
                }
                for (Edge edge : list) {
                    if (edge.isConditional() || !edge.label().isBlank()) {
                        return true;
                    }
                }
            }
            for (List<Edge> list : incoming.values()) {
                if (list.size() > 1) {
                    return true;
                }
            }
            return false;
        }

        private static Map<String, List<Edge>> copyEdgeMap(Map<String, List<Edge>> source) {
            Map<String, List<Edge>> copy = new LinkedHashMap<>();
            source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
            return Collections.unmodifiableMap(copy);
        }

        private static Map<String, Set<String>> copySetMap(Map<String, Set<String>> source) {
            Map<String, Set<String>> copy = new LinkedHashMap<>();
            source.forEach((key, value) -> copy.put(key, Collections.unmodifiableSet(new LinkedHashSet<>(value))));
            return Collections.unmodifiableMap(copy);
        }
    }

    private ScenarioGraph() {
    }

    public static Plan plan(ScenarioItem scenario) {
        if (scenario == null || scenario.steps().isEmpty()) {
            return invalid("Scenario has no steps");
        }
        List<Step> steps = scenario.steps();
        Map<String, Step> byId = new LinkedHashMap<>();
        for (Step step : steps) {
            if (byId.put(step.id(), step) != null) {
                return invalid("Two steps share the id " + step.id());
            }
        }
        if (scenario.edges().isEmpty()) {
            return linearPlan(steps, byId);
        }
        return graphPlan(steps, byId, scenario.edges());
    }

    public static Set<String> bestEffortAncestors(ScenarioItem scenario, String stepId) {
        if (scenario == null || stepId == null || stepId.isBlank()) {
            return Set.of();
        }
        Plan planned = plan(scenario);
        if (planned.valid()) {
            return planned.ancestorsOf(stepId);
        }
        if (scenario.edges().isEmpty()) {
            Set<String> earlier = new LinkedHashSet<>();
            for (Step step : scenario.steps()) {
                if (step.id().equals(stepId)) {
                    break;
                }
                earlier.add(step.id());
            }
            return earlier;
        }
        Map<String, List<String>> parents = new HashMap<>();
        for (Edge edge : scenario.edges()) {
            if (!edge.from().isBlank() && !edge.to().isBlank() && !edge.from().equals(edge.to())) {
                parents.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge.from());
            }
        }
        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();
        stack.push(stepId);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            for (String parent : parents.getOrDefault(current, List.of())) {
                if (!parent.equals(stepId) && visited.add(parent)) {
                    stack.push(parent);
                }
            }
        }
        List<String> ordered = new ArrayList<>();
        for (Step step : scenario.steps()) {
            if (visited.contains(step.id())) {
                ordered.add(step.id());
            }
        }
        return new LinkedHashSet<>(ordered);
    }

    private static Plan linearPlan(List<Step> steps, Map<String, Step> byId) {
        Map<String, List<Edge>> outgoing = new LinkedHashMap<>();
        Map<String, List<Edge>> incoming = new LinkedHashMap<>();
        Map<String, Set<String>> ancestors = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            ancestors.put(step.id(), new LinkedHashSet<>(seen));
            seen.add(step.id());
            if (i > 0) {
                Edge edge = new Edge(steps.get(i - 1).id(), step.id());
                outgoing.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
                incoming.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge);
            }
        }
        return new Plan(steps, byId, outgoing, incoming, ancestors, "");
    }

    private static Plan graphPlan(List<Step> steps, Map<String, Step> byId, List<Edge> edges) {
        Set<String> edgeIds = new HashSet<>();
        Map<String, List<Edge>> outgoing = new LinkedHashMap<>();
        Map<String, List<Edge>> incoming = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (Step step : steps) {
            inDegree.put(step.id(), 0);
        }
        for (Edge edge : edges) {
            if (!edge.id().isBlank() && !edgeIds.add(edge.id())) {
                return invalid("Two connections share the id " + edge.id());
            }
            if (!byId.containsKey(edge.from()) || !byId.containsKey(edge.to())) {
                return invalid("A connection points to a step that does not exist");
            }
            if (edge.from().equals(edge.to())) {
                return invalid("Step " + label(byId.get(edge.from())) + " is connected to itself");
            }
            outgoing.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge);
            inDegree.merge(edge.to(), 1, Integer::sum);
        }
        for (Map.Entry<String, List<Edge>> entry : outgoing.entrySet()) {
            List<Edge> out = entry.getValue();
            if (out.size() > 1) {
                long elseCount = out.stream().filter(e -> e.condition().isElse()).count();
                if (elseCount > 1) {
                    return invalid("Step " + label(byId.get(entry.getKey())) + " has more than one Else branch");
                }
                boolean hasAlways = out.stream().anyMatch(e -> e.condition().mode() == EdgeCondition.MatchMode.ALWAYS);
                if (hasAlways) {
                    return invalid("Step " + label(byId.get(entry.getKey()))
                            + " has an unconditional branch alongside other branches");
                }
            }
        }
        List<Step> roots = steps.stream().filter(step -> inDegree.get(step.id()) == 0).toList();
        if (roots.isEmpty()) {
            return invalid("The steps form a loop, loops are not supported yet");
        }
        if (roots.size() > 1) {
            return invalid("The steps are not connected in one flow");
        }
        ArrayDeque<String> ready = new ArrayDeque<>();
        ready.add(roots.get(0).id());
        List<Step> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String current = ready.poll();
            ordered.add(byId.get(current));
            for (Edge edge : outgoing.getOrDefault(current, List.of())) {
                int remaining = inDegree.merge(edge.to(), -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(edge.to());
                }
            }
        }
        if (ordered.size() != steps.size()) {
            return invalid("The steps form a loop, loops are not supported yet");
        }
        Map<String, Set<String>> ancestors = new LinkedHashMap<>();
        for (Step step : ordered) {
            LinkedHashSet<String> anc = new LinkedHashSet<>();
            for (Edge edge : incoming.getOrDefault(step.id(), List.of())) {
                anc.addAll(ancestors.getOrDefault(edge.from(), Set.of()));
                anc.add(edge.from());
            }
            ancestors.put(step.id(), anc);
        }
        return new Plan(ordered, byId, outgoing, incoming, ancestors, "");
    }

    private static Plan invalid(String problem) {
        return new Plan(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), problem);
    }

    private static String label(Step step) {
        return "\"" + (step.name().isBlank() ? step.id() : step.name()) + "\"";
    }
}
