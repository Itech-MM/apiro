package io.github.itech_mm.apiro.model;

public record Assertion(Source source, String expression, Operator operator, String expected) {

    public enum Source {
        STATUS, JSON_PATH, HEADER, RESPONSE_TIME, BODY
    }

    public enum Operator {
        EQUALS, NOT_EQUALS, CONTAINS, NOT_CONTAINS, EXISTS, NOT_EXISTS, GREATER_THAN, GREATER_OR_EQUAL, LESS_THAN,
        LESS_OR_EQUAL
    }

    public Assertion {
        source = source == null ? Source.STATUS : source;
        expression = expression == null ? "" : expression;
        operator = operator == null ? Operator.EQUALS : operator;
        expected = expected == null ? "" : expected;
    }
}
