package io.github.itech_mm.apiro.model;

import java.util.List;

public record EdgeCondition(MatchMode mode, List<BranchRule> rules) {

    public enum MatchMode {
        ALWAYS, ALL, ANY, ELSE
    }

    public static final EdgeCondition ALWAYS = new EdgeCondition(MatchMode.ALWAYS, List.of());
    public static final EdgeCondition ELSE = new EdgeCondition(MatchMode.ELSE, List.of());

    public EdgeCondition {
        mode = mode == null ? MatchMode.ALWAYS : mode;
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static EdgeCondition all(BranchRule... rules) {
        return new EdgeCondition(MatchMode.ALL, List.of(rules));
    }

    public static EdgeCondition all(List<BranchRule> rules) {
        return new EdgeCondition(MatchMode.ALL, rules);
    }

    public static EdgeCondition any(BranchRule... rules) {
        return new EdgeCondition(MatchMode.ANY, List.of(rules));
    }

    public static EdgeCondition any(List<BranchRule> rules) {
        return new EdgeCondition(MatchMode.ANY, rules);
    }

    public boolean isUnconditional() {
        return mode == MatchMode.ALWAYS;
    }

    public boolean isElse() {
        return mode == MatchMode.ELSE;
    }

    public String describe() {
        return switch (mode) {
            case ALWAYS -> "";
            case ELSE -> "else";
            case ALL, ANY -> {
                if (rules.isEmpty()) {
                    yield mode == MatchMode.ALL ? "all" : "any";
                }
                if (rules.size() == 1) {
                    yield rules.get(0).describe();
                }
                String joiner = mode == MatchMode.ALL ? " && " : " || ";
                yield rules.stream().map(BranchRule::describe).collect(java.util.stream.Collectors.joining(joiner));
            }
        };
    }
}
