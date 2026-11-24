package io.github.itech_mm.apiro.service.scenario;

import com.google.gson.JsonElement;
import io.github.itech_mm.apiro.model.Extractor;
import io.github.itech_mm.apiro.model.Variable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class Extractors {

    public record Result(Extractor extractor, boolean success, String value, String error) {

        static Result ok(Extractor extractor, String value) {
            return new Result(extractor, true, value, "");
        }

        static Result failed(Extractor extractor, String error) {
            return new Result(extractor, false, "", error);
        }
    }

    private Extractors() {
    }

    public static List<Result> extractAll(List<Extractor> extractors, ResponseContext context) {
        List<Result> results = new ArrayList<>();
        for (Extractor extractor : extractors) {
            results.add(extract(extractor, context));
        }
        return results;
    }

    public static List<Variable> toVariables(List<Result> results) {
        List<Variable> variables = new ArrayList<>();
        for (Result result : results) {
            if (result.success()) {
                variables.add(Variable.of(result.extractor().variable(), result.value()));
            }
        }
        return variables;
    }

    public static Result extract(Extractor extractor, ResponseContext context) {
        if (extractor.variable().isEmpty()) {
            return Result.failed(extractor, "Variable name is empty");
        }
        try {
            return switch (extractor.source()) {
                case STATUS -> Result.ok(extractor, String.valueOf(context.status()));
                case HEADER -> context.header(extractor.expression())
                        .map(value -> Result.ok(extractor, value))
                        .orElseGet(() -> Result.failed(extractor, "Header " + extractor.expression() + " not found"));
                case JSON_PATH -> fromJson(extractor, context);
                case REGEX -> fromRegex(extractor, context);
            };
        } catch (IllegalArgumentException e) {
            return Result.failed(extractor, e.getMessage());
        }
    }

    private static Result fromJson(Extractor extractor, ResponseContext context) {
        Optional<JsonElement> found = context.jsonValue(extractor.expression());
        if (found.isEmpty()) {
            return Result.failed(extractor, "JSON path " + extractor.expression() + " not found");
        }
        return Result.ok(extractor, ResponseContext.text(found.get()));
    }

    private static Result fromRegex(Extractor extractor, ResponseContext context) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(extractor.expression());
        } catch (PatternSyntaxException e) {
            return Result.failed(extractor, "Invalid regex: " + e.getDescription());
        }
        Matcher matcher = pattern.matcher(context.body());
        if (!matcher.find()) {
            return Result.failed(extractor, "Regex did not match the response body");
        }
        String value = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
        if (value == null) {
            return Result.failed(extractor, "Regex group 1 did not match");
        }
        return Result.ok(extractor, value);
    }
}
