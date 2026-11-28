package io.github.itech_mm.apiro.service.scenario;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.github.itech_mm.apiro.model.BranchRule;
import io.github.itech_mm.apiro.model.EdgeCondition;
import io.github.itech_mm.apiro.service.variables.VariableResolver;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class BranchEvaluator {

    public record RuleResult(BranchRule rule, boolean matched, String actual, String message) {

        public RuleResult {
            actual = actual == null ? "" : actual;
            message = message == null ? "" : message;
        }
    }

    public record Evaluation(boolean matched, String reason, List<RuleResult> rules) {

        public Evaluation {
            reason = reason == null ? "" : reason;
            rules = rules == null ? List.of() : List.copyOf(rules);
        }
    }

    private record Reading(boolean available, Optional<String> text, JsonElement element, String unavailableReason) {
    }

    private BranchEvaluator() {
    }

    public static Evaluation evaluate(EdgeCondition condition, StepResult stepResult, VariableResolver resolver) {
        EdgeCondition effective = condition == null ? EdgeCondition.ALWAYS : condition;
        return switch (effective.mode()) {
            case ALWAYS -> new Evaluation(true, "always", List.of());
            case ELSE -> new Evaluation(true, "else", List.of());
            case ALL, ANY -> evaluateRules(effective, stepResult, resolver);
        };
    }

    public static boolean usesStepOutcome(EdgeCondition condition) {
        if (condition == null) {
            return false;
        }
        if (condition.mode() != EdgeCondition.MatchMode.ALL && condition.mode() != EdgeCondition.MatchMode.ANY) {
            return false;
        }
        return condition.rules().stream().anyMatch(rule -> rule.source() == BranchRule.Source.STEP_OUTCOME);
    }

    private static Evaluation evaluateRules(EdgeCondition condition, StepResult stepResult, VariableResolver resolver) {
        List<BranchRule> rules = condition.rules();
        if (rules.isEmpty()) {
            boolean matched = condition.mode() == EdgeCondition.MatchMode.ALL;
            return new Evaluation(matched, matched ? "all (no rules)" : "any (no rules)", List.of());
        }
        ResponseContext response = stepResult != null && stepResult.exchange() != null
                ? ResponseContext.of(stepResult.exchange().response())
                : null;
        List<RuleResult> results = new ArrayList<>(rules.size());
        boolean allMatched = true;
        boolean anyMatched = false;
        for (BranchRule rule : rules) {
            RuleResult result = checkRule(rule, stepResult, response, resolver);
            results.add(result);
            if (result.matched()) {
                anyMatched = true;
            } else {
                allMatched = false;
            }
        }
        boolean matched = condition.mode() == EdgeCondition.MatchMode.ALL ? allMatched : anyMatched;
        return new Evaluation(matched, condition.describe(), results);
    }

    private static RuleResult checkRule(BranchRule rule, StepResult stepResult, ResponseContext response,
                                        VariableResolver resolver) {
        Reading reading;
        try {
            reading = read(rule, stepResult, response, resolver);
        } catch (IllegalArgumentException error) {
            return new RuleResult(rule, false, "", error.getMessage());
        }
        if (!reading.available()) {
            return new RuleResult(rule, false, "", reading.unavailableReason());
        }
        String expected = resolver == null ? rule.expected() : resolver.resolve(rule.expected());
        boolean present = reading.text().isPresent()
                && !(rule.source() == BranchRule.Source.BODY && reading.text().get().isEmpty());
        String actual = reading.text().orElse("");
        switch (rule.operator()) {
            case EXISTS:
                return new RuleResult(rule, present, actual, present ? rule.describe() : "missing");
            case NOT_EXISTS:
                return new RuleResult(rule, !present, actual, !present ? rule.describe() : "present");
            case IS_EMPTY:
                boolean empty = isEmptyValue(reading, present, actual);
                return new RuleResult(rule, empty, actual, empty ? rule.describe() : "not empty");
            case NOT_EMPTY:
                boolean notEmpty = !isEmptyValue(reading, present, actual);
                return new RuleResult(rule, notEmpty, actual, notEmpty ? rule.describe() : "empty");
            default:
                break;
        }
        if (reading.text().isEmpty()) {
            boolean negated = rule.operator() == BranchRule.Operator.NOT_EQUALS
                    || rule.operator() == BranchRule.Operator.NOT_CONTAINS;
            return new RuleResult(rule, negated, "", negated ? rule.describe() + " (value not found)"
                    : "value not found");
        }
        return compare(rule, reading, actual, expected);
    }

    private static RuleResult compare(BranchRule rule, Reading reading, String actual, String expected) {
        boolean ignoreCase = rule.source() == BranchRule.Source.STEP_OUTCOME;
        return switch (rule.operator()) {
            case EQUALS -> {
                boolean ok = ignoreCase ? actual.equalsIgnoreCase(expected) : same(actual, expected);
                yield new RuleResult(rule, ok, actual, ok ? rule.describe() : actual + " != " + expected);
            }
            case NOT_EQUALS -> {
                boolean ok = ignoreCase ? !actual.equalsIgnoreCase(expected) : !same(actual, expected);
                yield new RuleResult(rule, ok, actual, ok ? rule.describe() : actual + " == " + expected);
            }
            case CONTAINS -> {
                boolean ok = contains(reading, expected);
                yield new RuleResult(rule, ok, actual, ok ? rule.describe() : "does not contain " + expected);
            }
            case NOT_CONTAINS -> {
                boolean ok = !contains(reading, expected);
                yield new RuleResult(rule, ok, actual, ok ? rule.describe() : "contains " + expected);
            }
            case MATCHES_REGEX -> {
                try {
                    boolean ok = Pattern.compile(expected).matcher(actual).matches();
                    yield new RuleResult(rule, ok, actual, ok ? rule.describe() : "does not match " + expected);
                } catch (PatternSyntaxException error) {
                    yield new RuleResult(rule, false, actual, "Invalid regex: " + error.getDescription());
                }
            }
            case GREATER_THAN, GREATER_OR_EQUAL, LESS_THAN, LESS_OR_EQUAL -> {
                Optional<BigDecimal> left = number(actual);
                Optional<BigDecimal> right = number(expected);
                if (left.isEmpty()) {
                    yield new RuleResult(rule, false, actual, "not a number: " + actual);
                }
                if (right.isEmpty()) {
                    yield new RuleResult(rule, false, actual, "expected is not a number: " + expected);
                }
                int order = left.get().compareTo(right.get());
                boolean ok = switch (rule.operator()) {
                    case GREATER_THAN -> order > 0;
                    case GREATER_OR_EQUAL -> order >= 0;
                    case LESS_THAN -> order < 0;
                    default -> order <= 0;
                };
                yield new RuleResult(rule, ok, actual, ok ? rule.describe() : actual + " failed comparison");
            }
            default -> new RuleResult(rule, false, actual, "unsupported operator");
        };
    }

    private static Reading read(BranchRule rule, StepResult stepResult, ResponseContext response,
                                VariableResolver resolver) {
        if (rule.source() == BranchRule.Source.STEP_OUTCOME) {
            String outcome = stepResult == null ? "" : stepResult.state().name();
            return new Reading(true, Optional.of(outcome), null, "");
        }
        if (rule.source() == BranchRule.Source.VARIABLE) {
            String name = rule.expression().trim();
            if (name.isEmpty() || resolver == null || !resolver.isDefined(name)) {
                return new Reading(true, Optional.empty(), null, "");
            }
            return new Reading(true, Optional.of(resolver.resolve("${" + name + "}")), null, "");
        }
        if (response == null) {
            return new Reading(false, Optional.empty(), null, "No HTTP response available");
        }
        return switch (rule.source()) {
            case STATUS -> new Reading(true, Optional.of(String.valueOf(response.status())), null, "");
            case RESPONSE_TIME -> new Reading(true, Optional.of(String.valueOf(response.millis())), null, "");
            case BODY -> new Reading(true, Optional.of(response.body()), null, "");
            case HEADER -> new Reading(true, response.header(rule.expression()), null, "");
            case JSON_PATH -> {
                JsonElement element = response.jsonValue(rule.expression()).orElse(null);
                yield new Reading(true, Optional.ofNullable(element).map(ResponseContext::text), element, "");
            }
            default -> new Reading(false, Optional.empty(), null, "Unsupported source");
        };
    }

    private static boolean isEmptyValue(Reading reading, boolean present, String actual) {
        if (!present) {
            return true;
        }
        if (reading.element() != null) {
            if (reading.element().isJsonNull()) {
                return true;
            }
            if (reading.element().isJsonArray()) {
                return reading.element().getAsJsonArray().isEmpty();
            }
            if (reading.element().isJsonObject()) {
                return reading.element().getAsJsonObject().isEmpty();
            }
        }
        return actual.isBlank();
    }

    private static boolean contains(Reading reading, String expected) {
        if (reading.element() != null && reading.element().isJsonArray()) {
            JsonArray array = reading.element().getAsJsonArray();
            for (JsonElement item : array) {
                if (same(ResponseContext.text(item), expected)) {
                    return true;
                }
            }
            return false;
        }
        return reading.text().orElse("").contains(expected);
    }

    private static boolean same(String actual, String expected) {
        Optional<BigDecimal> left = number(actual);
        Optional<BigDecimal> right = number(expected);
        if (left.isPresent() && right.isPresent()) {
            return left.get().compareTo(right.get()) == 0;
        }
        return actual.equals(expected);
    }

    private static Optional<BigDecimal> number(String text) {
        try {
            return Optional.of(new BigDecimal(text.trim()));
        } catch (NumberFormatException error) {
            return Optional.empty();
        }
    }
}
