package io.github.itech_mm.apiro.model;

import java.util.UUID;

public record Edge(String id, String from, String to, EdgeCondition condition, String label) {

    public Edge {
        id = id == null ? "" : id;
        from = from == null ? "" : from;
        to = to == null ? "" : to;
        condition = condition == null ? EdgeCondition.ALWAYS : condition;
        label = label == null ? "" : label;
    }

    public Edge(String from, String to) {
        this("", from, to, EdgeCondition.ALWAYS, "");
    }

    public static Edge of(String from, String to) {
        return new Edge(UUID.randomUUID().toString(), from, to, EdgeCondition.ALWAYS, "");
    }

    public static Edge of(String from, String to, EdgeCondition condition, String label) {
        return new Edge(UUID.randomUUID().toString(), from, to, condition, label);
    }

    public boolean isConditional() {
        return !condition.isUnconditional();
    }

    public String displayLabel() {
        return label.isBlank() ? condition.describe() : label;
    }
}
