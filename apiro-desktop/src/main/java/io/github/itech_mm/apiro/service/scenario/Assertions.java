package io.github.itech_mm.apiro.service.scenario;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.github.itech_mm.apiro.model.Assertion;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

public final class Assertions {

    public record Result(Assertion assertion, boolean passed, String actual, String message) {
    }

    private record Reading(Optional<String> text, JsonElement element) {
    }

    private static final int SHOWN = 200;

    private Assertions() {
    }

    public static List<Result> checkAll(List<Assertion> assertions, ResponseContext context,
                                        UnaryOperator<String> expectedResolver) {
        List<Result> results = new ArrayList<>();
        for (Assertion assertion : assertions) {
            results.add(check(assertion, context, expectedResolver));
        }
        return results;
    }

    public static Result check(Assertion assertion, ResponseContext context) {
        return check(assertion, context, UnaryOperator.identity());
    }

    public static Result check(Assertion assertion, ResponseContext context, UnaryOperator<String> expectedResolver) {
        Reading reading;
        try {
            reading = read(assertion, context);
        } catch (IllegalArgumentException e) {
            return new Result(assertion, false, "", e.getMessage());
        }
        String expected = expectedResolver.apply(assertion.expected());
        String label = label(assertion);
        boolean present = reading.text().isPresent()
                && !(assertion.source() == Assertion.Source.BODY && reading.text().get().isEmpty());
        String actual = reading.text().orElse("");
        switch (assertion.operator()) {
            case EXISTS:
                return outcome(assertion, present, actual, expected, label, present ? null : label + " does not exist");
            case NOT_EXISTS:
                return outcome(assertion, !present, actual, expected, label,
                        present ? label + " exists but should not" : null);
            default:
                break;
        }
        if (reading.text().isEmpty()) {
            return new Result(assertion, false, "", label + " was not found");
        }
        return compare(assertion, reading, expected, label);
    }

    private static Result compare(Assertion assertion, Reading reading, String expected, String label) {
        String actual = reading.text().orElse("");
        boolean passed;
        switch (assertion.operator()) {
            case EQUALS -> passed = same(actual, expected);
            case NOT_EQUALS -> passed = !same(actual, expected);
            case CONTAINS -> passed = contains(reading, expected);
            case NOT_CONTAINS -> passed = !contains(reading, expected);
            default -> {
                Optional<BigDecimal> left = number(actual);
                Optional<BigDecimal> right = number(expected);
                if (left.isEmpty()) {
                    return new Result(assertion, false, actual, label + " is not a number: " + shorten(actual));
                }
                if (right.isEmpty()) {
                    return new Result(assertion, false, actual, "Expected value is not a number: " + shorten(expected));
                }
                int order = left.get().compareTo(right.get());
                passed = switch (assertion.operator()) {
                    case GREATER_THAN -> order > 0;
                    case GREATER_OR_EQUAL -> order >= 0;
                    case LESS_THAN -> order < 0;
                    default -> order <= 0;
                };
            }
        }
        return outcome(assertion, passed, actual, expected, label, null);
    }

    private static Result outcome(Assertion assertion, boolean passed, String actual, String expected, String label,
                                  String failure) {
        if (passed) {
            return new Result(assertion, true, actual, label + " " + phrase(assertion, expected, true));
        }
        String message = failure != null ? failure
                : "Expected " + label + " to " + phrase(assertion, expected, false) + " but was " + shorten(actual);
        return new Result(assertion, false, actual, message);
    }

    private static Reading read(Assertion assertion, ResponseContext context) {
        return switch (assertion.source()) {
            case STATUS -> new Reading(Optional.of(String.valueOf(context.status())), null);
            case RESPONSE_TIME -> new Reading(Optional.of(String.valueOf(context.millis())), null);
            case BODY -> new Reading(Optional.of(context.body()), null);
            case HEADER -> new Reading(context.header(assertion.expression()), null);
            case JSON_PATH -> {
                JsonElement element = context.jsonValue(assertion.expression()).orElse(null);
                yield new Reading(Optional.ofNullable(element).map(ResponseContext::text), element);
            }
        };
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
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String label(Assertion assertion) {
        return switch (assertion.source()) {
            case STATUS -> "status";
            case RESPONSE_TIME -> "response time";
            case BODY -> "body";
            case HEADER -> "header " + assertion.expression();
            case JSON_PATH -> assertion.expression();
        };
    }

    private static String phrase(Assertion assertion, String expected, boolean passed) {
        String verb = switch (assertion.operator()) {
            case EQUALS -> passed ? "equals" : "equal";
            case NOT_EQUALS -> passed ? "does not equal" : "not equal";
            case CONTAINS -> passed ? "contains" : "contain";
            case NOT_CONTAINS -> passed ? "does not contain" : "not contain";
            case EXISTS -> passed ? "exists" : "exist";
            case NOT_EXISTS -> passed ? "does not exist" : "not exist";
            case GREATER_THAN -> passed ? "is greater than" : "be greater than";
            case GREATER_OR_EQUAL -> passed ? "is at least" : "be at least";
            case LESS_THAN -> passed ? "is less than" : "be less than";
            case LESS_OR_EQUAL -> passed ? "is at most" : "be at most";
        };
        boolean unary = assertion.operator() == Assertion.Operator.EXISTS
                || assertion.operator() == Assertion.Operator.NOT_EXISTS;
        return unary ? verb : verb + " " + shorten(expected);
    }

    private static String shorten(String text) {
        return text.length() <= SHOWN ? text : text.substring(0, SHOWN) + "...";
    }
}
