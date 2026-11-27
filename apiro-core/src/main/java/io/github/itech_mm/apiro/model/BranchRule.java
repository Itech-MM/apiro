package io.github.itech_mm.apiro.model;

public record BranchRule(Source source, String expression, Operator operator, String expected) {

    public enum Source {
        STATUS, JSON_PATH, HEADER, RESPONSE_TIME, BODY, VARIABLE, STEP_OUTCOME
    }

    public enum Operator {
        EQUALS, NOT_EQUALS, CONTAINS, NOT_CONTAINS, EXISTS, NOT_EXISTS, GREATER_THAN, GREATER_OR_EQUAL, LESS_THAN,
        LESS_OR_EQUAL, MATCHES_REGEX, IS_EMPTY, NOT_EMPTY
    }

    public BranchRule {
        source = source == null ? Source.STATUS : source;
        expression = expression == null ? "" : expression;
        operator = operator == null ? Operator.EQUALS : operator;
        expected = expected == null ? "" : expected;
    }

    public static BranchRule of(Source source, String expression, Operator operator, String expected) {
        return new BranchRule(source, expression, operator, expected);
    }

    public static BranchRule status(Operator operator, String expected) {
        return new BranchRule(Source.STATUS, "", operator, expected);
    }

    public static BranchRule jsonPath(String path, Operator operator, String expected) {
        return new BranchRule(Source.JSON_PATH, path, operator, expected);
    }

    public static BranchRule header(String name, Operator operator, String expected) {
        return new BranchRule(Source.HEADER, name, operator, expected);
    }

    public static BranchRule variable(String name, Operator operator, String expected) {
        return new BranchRule(Source.VARIABLE, name, operator, expected);
    }

    public static BranchRule stepPassed() {
        return new BranchRule(Source.STEP_OUTCOME, "", Operator.EQUALS, "PASSED");
    }

    public static BranchRule stepFailed() {
        return new BranchRule(Source.STEP_OUTCOME, "", Operator.EQUALS, "FAILED");
    }

    public boolean needsExpression() {
        return source == Source.JSON_PATH || source == Source.HEADER || source == Source.VARIABLE;
    }

    public boolean needsExpected() {
        return operator != Operator.EXISTS && operator != Operator.NOT_EXISTS
                && operator != Operator.IS_EMPTY && operator != Operator.NOT_EMPTY;
    }

    public String describe() {
        String subject = switch (source) {
            case STATUS -> "status";
            case RESPONSE_TIME -> "time";
            case BODY -> "body";
            case HEADER -> "header " + expression;
            case JSON_PATH -> expression.isBlank() ? "json" : expression;
            case VARIABLE -> "${" + expression + "}";
            case STEP_OUTCOME -> "outcome";
        };
        String verb = switch (operator) {
            case EQUALS -> "=";
            case NOT_EQUALS -> "!=";
            case CONTAINS -> "contains";
            case NOT_CONTAINS -> "!contains";
            case EXISTS -> "exists";
            case NOT_EXISTS -> "!exists";
            case GREATER_THAN -> ">";
            case GREATER_OR_EQUAL -> ">=";
            case LESS_THAN -> "<";
            case LESS_OR_EQUAL -> "<=";
            case MATCHES_REGEX -> "~=";
            case IS_EMPTY -> "is empty";
            case NOT_EMPTY -> "not empty";
        };
        return needsExpected() ? subject + " " + verb + " " + expected : subject + " " + verb;
    }
}
