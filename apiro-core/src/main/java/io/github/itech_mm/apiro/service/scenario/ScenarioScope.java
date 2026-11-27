package io.github.itech_mm.apiro.service.scenario;

import io.github.itech_mm.apiro.model.Extractor;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.model.Variable;
import io.github.itech_mm.apiro.service.variables.VariableResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ScenarioScope {

    private ScenarioScope() {
    }

    public static VariableResolver forStep(Project project, String environmentId, ScenarioItem scenario, Step step) {
        List<Variable> extracted = new ArrayList<>();
        Map<String, Step> byId = new LinkedHashMap<>();
        for (Step candidate : scenario.steps()) {
            byId.put(candidate.id(), candidate);
        }
        Set<String> ancestors = ScenarioGraph.bestEffortAncestors(scenario, step.id());
        for (String earlierId : ancestors) {
            Step earlier = byId.get(earlierId);
            if (earlier != null) {
                addExtracted(extracted, earlier);
            }
        }
        return VariableResolver.forItem(project, environmentId, step.requestId(), scenario.variables(), extracted);
    }

    public static VariableResolver afterStep(Project project, String environmentId, ScenarioItem scenario, Step step) {
        List<Variable> extracted = new ArrayList<>();
        Map<String, Step> byId = new LinkedHashMap<>();
        for (Step candidate : scenario.steps()) {
            byId.put(candidate.id(), candidate);
        }
        Set<String> ancestors = ScenarioGraph.bestEffortAncestors(scenario, step.id());
        for (String earlierId : ancestors) {
            Step earlier = byId.get(earlierId);
            if (earlier != null) {
                addExtracted(extracted, earlier);
            }
        }
        addExtracted(extracted, step);
        return VariableResolver.forItem(project, environmentId, step.requestId(), scenario.variables(), extracted);
    }

    private static void addExtracted(List<Variable> out, Step step) {
        for (Extractor extractor : step.extractors()) {
            if (!extractor.variable().isBlank()) {
                out.add(Variable.of(extractor.variable(), ""));
            }
        }
    }
}
