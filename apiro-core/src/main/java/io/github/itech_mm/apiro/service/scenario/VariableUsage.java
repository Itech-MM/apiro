package io.github.itech_mm.apiro.service.scenario;

import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.Extractor;
import io.github.itech_mm.apiro.model.FormPart;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.service.project.Inheritance;
import io.github.itech_mm.apiro.service.variables.VariableResolver;
import java.util.ArrayList;
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

    public record Result(List<Link> links, Map<String, List<String>> unresolved) {

        public static final Result EMPTY = new Result(List.of(), Map.of());

        public Result {
            links = List.copyOf(links);
            unresolved = Map.copyOf(unresolved);
        }

        public List<String> unresolvedFor(String stepId) {
            return unresolved.getOrDefault(stepId, List.of());
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
        List<Step> steps = scenario.steps();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            Optional<RequestItem> request = project.findItem(step.requestId()).filter(RequestItem.class::isInstance)
                    .map(RequestItem.class::cast);
            if (request.isEmpty()) {
                continue;
            }
            VariableResolver scope = ScenarioScope.forStep(project, environmentId, scenario, step);
            Map<String, Set<String>> byProducer = new LinkedHashMap<>();
            Set<String> missing = new LinkedHashSet<>();
            for (String name : referencedNames(project, request.get(), step, scope)) {
                int producer = producerBefore(steps, i, name);
                if (producer >= 0) {
                    byProducer.computeIfAbsent(steps.get(producer).id(), key -> new LinkedHashSet<>()).add(name);
                } else if (!scope.isDefined(name)) {
                    missing.add(name);
                }
            }
            byProducer.forEach((from, names) -> links.add(new Link(from, step.id(), new ArrayList<>(names))));
            if (!missing.isEmpty()) {
                unresolved.put(step.id(), new ArrayList<>(missing));
            }
        }
        return new Result(links, unresolved);
    }

    private static int producerBefore(List<Step> steps, int index, String name) {
        for (int i = index - 1; i >= 0; i--) {
            for (Extractor extractor : steps.get(i).extractors()) {
                if (extractor.variable().equals(name)) {
                    return i;
                }
            }
        }
        return -1;
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
