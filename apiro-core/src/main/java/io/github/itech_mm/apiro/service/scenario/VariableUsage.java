package io.github.itech_mm.apiro.service.scenario;

import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.BranchRule;
import io.github.itech_mm.apiro.model.Edge;
import io.github.itech_mm.apiro.model.EdgeCondition;
import io.github.itech_mm.apiro.model.Extractor;
import io.github.itech_mm.apiro.model.FieldOverride;
import io.github.itech_mm.apiro.model.FormPart;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.service.project.Inheritance;
import io.github.itech_mm.apiro.service.variables.VariableResolver;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class VariableUsage {

    public record Link(String fromStepId, String toStepId, List<String> names) {

        public Link {
            names = List.copyOf(names);
        }
    }

    public record Result(List<Link> links, Map<String, List<String>> unresolved,
                         Map<String, List<String>> unresolvedEdges) {

        public static final Result EMPTY = new Result(List.of(), Map.of(), Map.of());

        public Result(List<Link> links, Map<String, List<String>> unresolved) {
            this(links, unresolved, Map.of());
        }

        public Result {
            links = List.copyOf(links);
            unresolved = Map.copyOf(unresolved);
            unresolvedEdges = unresolvedEdges == null ? Map.of() : Map.copyOf(unresolvedEdges);
        }

        public List<String> unresolvedFor(String stepId) {
            return unresolved.getOrDefault(stepId, List.of());
        }

        public List<String> unresolvedForEdge(String edgeId) {
            return unresolvedEdges.getOrDefault(edgeId, List.of());
        }
    }

    private VariableUsage() {
    }

    public static Result analyze(Project project, String environmentId, ScenarioItem scenario) {
        if (project == null || scenario == null) {
            return Result.EMPTY;
        }
        List<Link> links = new ArrayList<>();
        Map<String, List<String>> unresolved = new LinkedHashMap<>();
        Map<String, List<String>> unresolvedEdges = new LinkedHashMap<>();
        List<Step> steps = scenario.steps();
        Map<String, Step> byId = new LinkedHashMap<>();
        for (Step step : steps) {
            byId.put(step.id(), step);
        }
        Map<String, List<String>> parentsOf = parentsMap(scenario, steps);
        for (Step step : steps) {
            Optional<RequestItem> request = project.findItem(step.requestId()).filter(RequestItem.class::isInstance)
                    .map(RequestItem.class::cast);
            if (request.isEmpty()) {
                continue;
            }
            VariableResolver scope = ScenarioScope.forStep(project, environmentId, scenario, step);
            Map<String, Set<String>> byProducer = new LinkedHashMap<>();
            Set<String> missing = new LinkedHashSet<>();
            for (String name : referencedNames(project, request.get(), step, scope)) {
                List<String> producers = nearestProducers(step.id(), parentsOf, byId, name);
                if (!producers.isEmpty()) {
                    for (String producer : producers) {
                        byProducer.computeIfAbsent(producer, key -> new LinkedHashSet<>()).add(name);
                    }
                } else if (!scope.isDefined(name)) {
                    missing.add(name);
                }
            }
            byProducer.forEach((from, names) -> links.add(new Link(from, step.id(), new ArrayList<>(names))));
            if (!missing.isEmpty()) {
                unresolved.put(step.id(), new ArrayList<>(missing));
            }
        }
        for (Edge edge : scenario.edges()) {
            if (edge.condition().mode() != EdgeCondition.MatchMode.ALL
                    && edge.condition().mode() != EdgeCondition.MatchMode.ANY) {
                continue;
            }
            Step from = byId.get(edge.from());
            if (from == null) {
                continue;
            }
            VariableResolver postStep = ScenarioScope.afterStep(project, environmentId, scenario, from);
            Set<String> missing = new LinkedHashSet<>();
            for (BranchRule rule : edge.condition().rules()) {
                if (rule.source() == BranchRule.Source.VARIABLE) {
                    String varName = rule.expression().trim();
                    if (!varName.isEmpty() && !postStep.isDefined(varName)) {
                        missing.add(varName);
                    }
                }
                for (VariableResolver.Reference reference : postStep.scan(rule.expected())) {
                    if (!reference.name().startsWith("$") && !postStep.isDefined(reference.name())) {
                        missing.add(reference.name());
                    }
                }
            }
            if (!missing.isEmpty() && !edge.id().isBlank()) {
                unresolvedEdges.put(edge.id(), new ArrayList<>(missing));
            }
        }
        return new Result(links, unresolved, unresolvedEdges);
    }

    private static Map<String, List<String>> parentsMap(ScenarioItem scenario, List<Step> steps) {
        Map<String, List<String>> parents = new HashMap<>();
        if (scenario.edges().isEmpty()) {
            for (int i = 1; i < steps.size(); i++) {
                parents.computeIfAbsent(steps.get(i).id(), k -> new ArrayList<>()).add(steps.get(i - 1).id());
            }
            return parents;
        }
        for (Edge edge : scenario.edges()) {
            if (!edge.from().isBlank() && !edge.to().isBlank() && !edge.from().equals(edge.to())) {
                parents.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge.from());
            }
        }
        return parents;
    }

    private static List<String> nearestProducers(String targetId, Map<String, List<String>> parentsOf,
                                                 Map<String, Step> byId, String name) {
        List<String> found = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (String parent : parentsOf.getOrDefault(targetId, List.of())) {
            if (visited.add(parent)) {
                queue.add(parent);
            }
        }
        while (!queue.isEmpty()) {
            String candidateId = queue.poll();
            Step candidate = byId.get(candidateId);
            if (candidate == null) {
                continue;
            }
            if (extracts(candidate, name)) {
                found.add(candidateId);
                continue;
            }
            for (String parent : parentsOf.getOrDefault(candidateId, List.of())) {
                if (!parent.equals(targetId) && visited.add(parent)) {
                    queue.add(parent);
                }
            }
        }
        return found;
    }

    private static boolean extracts(Step step, String name) {
        for (Extractor extractor : step.extractors()) {
            if (extractor.variable().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> referencedNames(Project project, RequestItem request, Step step,
                                               VariableResolver scope) {
        Set<String> names = new LinkedHashSet<>();
        for (String text : texts(project, request, step)) {
            for (VariableResolver.Reference reference : scope.scan(text)) {
                if (!reference.name().startsWith("$")) {
                    names.add(reference.name());
                }
            }
        }
        return names;
    }

    private static List<String> texts(Project project, RequestItem request, Step step) {
        List<String> texts = new ArrayList<>();
        texts.add(request.url());
        addPairs(texts, request.query());
        Optional<Inheritance.Effective> effective = Inheritance.forItem(project, request.id());
        addPairs(texts, effective.map(Inheritance.Effective::headers).orElse(request.headers()));
        addPairs(texts, step.headers());
        AuthConfig auth = effective.map(Inheritance.Effective::auth).orElse(request.auth());
        if (auth != null) {
            texts.add(auth.username());
            texts.add(auth.password());
            texts.add(auth.token());
            texts.add(auth.keyName());
            texts.add(auth.keyValue());
        }
        texts.add(request.body().text());
        if (step.bodyOverride() != null) {
            texts.add(step.bodyOverride().text());
            for (FieldOverride field : step.bodyOverride().fields()) {
                if (field.enabled()) {
                    texts.add(field.value());
                }
            }
        }
        addPairs(texts, request.body().form());
        for (FormPart part : request.body().parts()) {
            if (part.enabled()) {
                texts.add(part.name());
                texts.add(part.value());
            }
        }
        return texts;
    }

    private static void addPairs(List<String> texts, List<KeyValue> pairs) {
        for (KeyValue pair : pairs) {
            if (pair.enabled()) {
                texts.add(pair.key());
                texts.add(pair.value());
            }
        }
    }
}
