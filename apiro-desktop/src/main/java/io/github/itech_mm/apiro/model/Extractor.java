package io.github.itech_mm.apiro.model;

public record Extractor(String variable, Source source, String expression) {

    public enum Source {
        JSON_PATH, HEADER, REGEX, STATUS
    }

    public Extractor {
        variable = variable == null ? "" : variable.trim();
        source = source == null ? Source.JSON_PATH : source;
        expression = expression == null ? "" : expression;
    }
}
