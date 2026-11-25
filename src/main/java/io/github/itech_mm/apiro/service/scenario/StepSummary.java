package io.github.itech_mm.apiro.service.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StepSummary {

    private static final int LIMIT = 300;

    private StepSummary() {
    }

    public static String describe(StepResult result) {
        List<String> lines = new ArrayList<>();
        lines.add(headline(result));
        if (!result.message().isBlank() && result.state() != StepResult.State.PASSED) {
            lines.add(limit(result.message()));
        }
        for (Extractors.Result extraction : result.extractions()) {
            if (!extraction.success()) {
                lines.add(limit("Extract " + extraction.extractor().variable() + " failed: " + extraction.error()));
                break;
            }
        }
        List<Assertions.Result> assertions = result.assertions();
        if (!assertions.isEmpty()) {
            long passed = assertions.stream().filter(Assertions.Result::passed).count();
            lines.add("Assertions: " + passed + " of " + assertions.size() + " passed");
            Assertions.Result last = assertions.get(assertions.size() - 1);
            lines.add(limit("Last: " + last.message()));
            assertions.stream().filter(item -> !item.passed()).findFirst().filter(item -> item != last)
                    .ifPresent(item -> lines.add(limit("First failure: " + item.message())));
        }
        return String.join("\n", lines);
    }

    public static String duration(long millis) {
        return millis < 1000 ? millis + " ms" : String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
    }

    private static String headline(StepResult result) {
        String state = switch (result.state()) {
            case PENDING -> "Waiting";
            case RUNNING -> "Running";
            case PASSED -> "Passed";
            case FAILED -> "Failed";
            case SKIPPED -> "Skipped";
            case CANCELLED -> "Cancelled";
        };
        StringBuilder text = new StringBuilder(state);
        if (result.status() > 0) {
            text.append(" · ").append(result.status());
        }
        if (result.millis() > 0) {
            text.append(" · ").append(duration(result.millis()));
        }
        return text.toString();
    }

    private static String limit(String text) {
        String single = text.replace('\n', ' ').trim();
        return single.length() <= LIMIT ? single : single.substring(0, LIMIT) + "...";
    }
}
