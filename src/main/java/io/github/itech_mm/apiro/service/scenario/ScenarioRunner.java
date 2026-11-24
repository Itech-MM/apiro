package io.github.itech_mm.apiro.service.scenario;

import io.github.itech_framework.api_client.utils.CancelToken;
import io.github.itech_mm.apiro.model.OnFailure;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.model.Variable;
import io.github.itech_mm.apiro.service.http.ExchangeResult;
import io.github.itech_mm.apiro.service.http.RequestPreparer;
import io.github.itech_mm.apiro.service.http.RequestPreparer.PreparedRequest;
import io.github.itech_mm.apiro.service.http.SendOutcome;
import io.github.itech_mm.apiro.service.variables.VariableResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ScenarioRunner {

    @FunctionalInterface
    public interface Sleeper {

        void sleep(long millis) throws InterruptedException;
    }

    private static final long SLICE_MILLIS = 50;
    private static final String CANCELLED = "Run cancelled";
    private static final String AFTER_FAILURE = "Skipped after an earlier failure";

    private final StepSender sender;
    private final Sleeper sleeper;

    public ScenarioRunner(StepSender sender) {
        this(sender, Thread::sleep);
    }

    public ScenarioRunner(StepSender sender, Sleeper sleeper) {
        this.sender = Objects.requireNonNull(sender);
        this.sleeper = Objects.requireNonNull(sleeper);
    }

    public ScenarioResult run(Project project, String environmentId, ScenarioItem scenario, CancelToken token) {
        return run(project, environmentId, scenario, token, ScenarioListener.NONE);
    }

    public ScenarioResult run(Project project, String environmentId, ScenarioItem scenario, CancelToken token,
                              ScenarioListener listener) {
        Objects.requireNonNull(project);
        Objects.requireNonNull(scenario);
        Objects.requireNonNull(token);
        Objects.requireNonNull(listener);
        long started = System.nanoTime();
        ScenarioChain.Result chain = ScenarioChain.order(scenario);
        if (!chain.valid()) {
            return finish(listener, new ScenarioResult(scenario.id(), ScenarioResult.State.INVALID, chain.problem(),
                    List.of(), elapsed(started)));
        }
        listener.onStarted(chain.steps());
        Map<String, String> extracted = new LinkedHashMap<>();
        List<StepResult> results = new ArrayList<>();
        String stopReason = null;
        boolean cancelled = false;
        StepResult firstFailure = null;
        for (Step step : chain.steps()) {
            if (stopReason != null) {
                StepResult skipped = StepResult.of(step, StepResult.State.SKIPPED, stopReason);
                results.add(skipped);
                listener.onStepFinished(skipped);
                continue;
            }
            listener.onStepStarted(step);
            StepResult result = execute(project, environmentId, scenario, step, extracted, token);
            results.add(result);
            listener.onStepFinished(result);
            if (result.state() == StepResult.State.CANCELLED) {
                cancelled = true;
                stopReason = CANCELLED;
            } else if (result.state() == StepResult.State.FAILED) {
                if (firstFailure == null) {
                    firstFailure = result;
                }
                if (policyOf(scenario, step) == OnFailure.STOP) {
                    stopReason = AFTER_FAILURE;
                }
            }
        }
        ScenarioResult.State state = cancelled ? ScenarioResult.State.CANCELLED
                : firstFailure != null ? ScenarioResult.State.FAILED : ScenarioResult.State.PASSED;
        String message = cancelled ? CANCELLED : firstFailure == null ? "" : describe(firstFailure);
        return finish(listener, new ScenarioResult(scenario.id(), state, message, results, elapsed(started)));
    }

    private StepResult execute(Project project, String environmentId, ScenarioItem scenario, Step step,
                               Map<String, String> extracted, CancelToken token) {
        if (!pause(step.delayMillis(), token)) {
            return StepResult.of(step, StepResult.State.CANCELLED, CANCELLED);
        }
        long started = System.nanoTime();
        if (step.requestId().isBlank()) {
            return failed(step, "Step has no request", started);
        }
        PreparedRequest prepared;
        try {
            prepared = RequestPreparer.prepare(project, environmentId, step.requestId(),
                    scenario.variables(), literal(extracted), step.headers());
        } catch (RuntimeException error) {
            return failed(step, messageOf(error), started);
        }
        if (!prepared.unresolved().isEmpty()) {
            return failed(step, "Unresolved variables: " + String.join(", ", prepared.unresolved()), started);
        }
        SendOutcome outcome;
        try {
            outcome = sender.send(prepared, token);
        } catch (RuntimeException error) {
            return failed(step, messageOf(error), started);
        }
        if (outcome instanceof SendOutcome.Cancelled) {
            return StepResult.of(step, StepResult.State.CANCELLED, CANCELLED);
        }
        if (outcome instanceof SendOutcome.Failed failure) {
            return failed(step, failure.message(), started);
        }
        return evaluate(project, environmentId, scenario, step, extracted, ((SendOutcome.Done) outcome).result(),
                started);
    }

    private static StepResult evaluate(Project project, String environmentId, ScenarioItem scenario, Step step,
                                       Map<String, String> extracted,
                                       ExchangeResult exchange, long started) {
        ResponseContext context = ResponseContext.of(exchange.response());
        List<Extractors.Result> extractions = Extractors.extractAll(step.extractors(), context);
        for (Variable variable : Extractors.toVariables(extractions)) {
            extracted.put(variable.key(), variable.value());
        }
        VariableResolver resolver = VariableResolver.forItem(project, environmentId, step.requestId(),
                scenario.variables(), literal(extracted));
        List<Assertions.Result> assertions = Assertions.checkAll(step.assertions(), context, resolver::resolve);
        List<String> problems = new ArrayList<>();
        for (Extractors.Result extraction : extractions) {
            if (!extraction.success()) {
                problems.add("Extract " + extraction.extractor().variable() + ": " + extraction.error());
            }
        }
        for (Assertions.Result assertion : assertions) {
            if (!assertion.passed()) {
                problems.add(assertion.message());
            }
        }
        StepResult.State state = problems.isEmpty() ? StepResult.State.PASSED : StepResult.State.FAILED;
        return new StepResult(step, state, String.join("; ", problems), elapsed(started), exchange, extractions,
                assertions);
    }

    private static List<Variable> literal(Map<String, String> extracted) {
        List<Variable> layer = new ArrayList<>();
        extracted.forEach((name, value) -> layer.add(Variable.of(name, value)));
        return layer;
    }

    private boolean pause(long millis, CancelToken token) {
        long remaining = millis;
        while (remaining > 0) {
            if (token.isCancelled()) {
                return false;
            }
            long slice = Math.min(remaining, SLICE_MILLIS);
            try {
                sleeper.sleep(slice);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return false;
            }
            remaining -= slice;
        }
        return !token.isCancelled();
    }

    private static OnFailure policyOf(ScenarioItem scenario, Step step) {
        return step.onFailure() == null ? scenario.onFailure() : step.onFailure();
    }

    private static StepResult failed(Step step, String message, long started) {
        return new StepResult(step, StepResult.State.FAILED, message, elapsed(started), null, null, null);
    }

    private static String describe(StepResult failure) {
        String name = failure.step().name().isBlank() ? failure.step().id() : failure.step().name();
        return "Step \"" + name + "\" failed: " + failure.message();
    }

    private static String messageOf(RuntimeException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static long elapsed(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private static ScenarioResult finish(ScenarioListener listener, ScenarioResult result) {
        listener.onFinished(result);
        return result;
    }
}
