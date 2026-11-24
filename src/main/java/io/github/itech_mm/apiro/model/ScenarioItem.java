package io.github.itech_mm.apiro.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record ScenarioItem(String id, String name, List<Variable> variables, List<Step> steps, List<Edge> edges,
                           OnFailure onFailure) implements ProjectItem {

    public ScenarioItem {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        name = name == null ? "" : name;
        variables = variables == null ? List.of() : List.copyOf(variables);
        steps = steps == null ? List.of() : List.copyOf(steps);
        edges = edges == null ? List.of() : List.copyOf(edges);
        onFailure = onFailure == null ? OnFailure.STOP : onFailure;
    }

    public static ScenarioItem of(String name) {
        return new ScenarioItem(null, name, null, null, null, null);
    }

    public Optional<Step> findStep(String stepId) {
        return steps.stream().filter(step -> step.id().equals(stepId)).findFirst();
    }
}
