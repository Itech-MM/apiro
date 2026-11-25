package io.github.itech_mm.apiro.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record Environment(String id, String name, List<Variable> variables) {

    public Environment {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        name = name == null ? "" : name;
        variables = variables == null ? List.of() : List.copyOf(variables);
    }

    public static Environment of(String name, List<Variable> variables) {
        return new Environment(null, name, variables);
    }

    public Optional<Variable> find(String key) {
        return variables.stream()
                .filter(v -> v.enabled() && v.key().equals(key))
                .findFirst();
    }
}