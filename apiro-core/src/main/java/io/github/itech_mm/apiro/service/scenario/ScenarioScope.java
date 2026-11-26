package io.github.itech_mm.apiro.service.scenario;

import io.github.itech_mm.apiro.model.Extractor;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.model.Variable;
import io.github.itech_mm.apiro.service.variables.VariableResolver;
import java.util.ArrayList;
import java.util.List;

public final class ScenarioScope {

    private ScenarioScope() {
    }

    public static VariableResolver forStep(Project project, String environmentId, ScenarioItem scenario, Step step) {
        List<Variable> extracted = new ArrayList<>();
        for (Step earlier : scenario.steps()) {
            if (earlier.id().equals(step.id())) {
                break;
            }
            for (Extractor extractor : earlier.extractors()) {
                if (!extractor.variable().isBlank()) {
                    extracted.add(Variable.of(extractor.variable(), ""));
                }
            }
        }
        return VariableResolver.forItem(project, environmentId, step.requestId(), scenario.variables(), extracted);
    }
}
