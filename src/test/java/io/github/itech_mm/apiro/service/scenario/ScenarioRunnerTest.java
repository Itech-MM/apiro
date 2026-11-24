package io.github.itech_mm.apiro.service.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_framework.api_client.utils.CancelToken;
import io.github.itech_mm.apiro.model.Assertion;
import io.github.itech_mm.apiro.model.Edge;
import io.github.itech_mm.apiro.model.Extractor;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.OnFailure;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ResponseData;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.model.Variable;
import io.github.itech_mm.apiro.service.http.ExchangeResult;
import io.github.itech_mm.apiro.service.http.FailureKind;
import io.github.itech_mm.apiro.service.http.RequestPreparer.PreparedRequest;
import io.github.itech_mm.apiro.service.http.SendOutcome;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioRunnerTest {

    private static final String BASE = "http://api.test";

    private static final class Fake implements StepSender {

        final Deque<SendOutcome> outcomes = new ArrayDeque<>();
        final List<PreparedRequest> sent = new ArrayList<>();
        Runnable afterFirst;

        Fake(SendOutcome... scripted) {
            outcomes.addAll(List.of(scripted));
        }

        @Override
        public SendOutcome send(PreparedRequest request, CancelToken token) {
            sent.add(request);
            SendOutcome outcome = outcomes.isEmpty() ? reply(200, "{}") : outcomes.poll();
            if (sent.size() == 1 && afterFirst != null) {
                afterFirst.run();
            }
            return outcome;
        }
    }

    private static SendOutcome reply(int status, String body) {
        ResponseData response = new ResponseData(status, "", 5, 2, body.length(), 1, List.of(), null, body);
        return new SendOutcome.Done(new ExchangeResult(response, null, null));
    }

    private static RequestItem request(String id, HttpMethod method, String path, List<KeyValue> headers) {
        return new RequestItem(id, id, method, BASE + path, null, headers, null, null, null);
    }

    private static RequestItem get(String id, String path) {
        return request(id, HttpMethod.GET, path, null);
    }

    private static Project project(RequestItem... requests) {
        return new Project(null, 0, "p", "Shop", null, null, null, null, null, null, List.of(requests));
    }

    private static Extractor json(String variable, String path) {
        return new Extractor(variable, Extractor.Source.JSON_PATH, path);
    }

    private static Assertion status(String expected) {
        return new Assertion(Assertion.Source.STATUS, "", Assertion.Operator.EQUALS, expected);
    }

    private static Assertion jsonEquals(String path, String expected) {
        return new Assertion(Assertion.Source.JSON_PATH, path, Assertion.Operator.EQUALS, expected);
    }

    private static Step step(String id, String requestId, List<Extractor> extractors, List<Assertion> assertions) {
        return new Step(id, requestId, id, extractors, assertions, 0, null, 0, 0);
    }

    private static Step plain(String id, String requestId) {
        return step(id, requestId, null, null);
    }

    private static ScenarioItem scenario(OnFailure policy, Step... steps) {
        return new ScenarioItem("sc", "Flow", null, List.of(steps), null, policy);
    }

    private static List<StepResult.State> states(ScenarioResult result) {
        return result.steps().stream().map(StepResult::state).toList();
    }

    private static ScenarioResult run(Fake fake, Project project, ScenarioItem scenario) {
        return new ScenarioRunner(fake).run(project, "", scenario, new CancelToken());
    }

    @Test
    void chainPassesAndExtractedValuesFeedLaterSteps() {
        Project project = project(
                request("register", HttpMethod.POST, "/register", null),
                request("login", HttpMethod.POST, "/login", null),
                request("create", HttpMethod.POST, "/orders", List.of(KeyValue.of("Authorization", "Bearer ${token}"))),
                get("fetch", "/orders/${orderId}"));
        Fake fake = new Fake(
                reply(201, "{\"id\":\"u1\"}"),
                reply(200, "{\"data\":{\"token\":\"abc\"}}"),
                reply(201, "{\"id\":\"o9\"}"),
                reply(200, "{\"state\":\"CONFIRMED\"}"));
        ScenarioItem flow = scenario(OnFailure.STOP,
                step("s1", "register", List.of(json("userId", "$.id")), List.of(status("201"))),
                step("s2", "login", List.of(json("token", "$.data.token")), List.of(status("200"))),
                step("s3", "create", List.of(json("orderId", "$.id")), null),
                step("s4", "fetch", null, List.of(jsonEquals("$.state", "CONFIRMED"))));

        ScenarioResult result = run(fake, project, flow);

        assertEquals(ScenarioResult.State.PASSED, result.state(), result.message());
        assertEquals(4, result.count(StepResult.State.PASSED));
        assertEquals("Bearer abc", header(fake.sent.get(2), "Authorization"));
        assertEquals(BASE + "/orders/o9", fake.sent.get(3).url());
    }

    @Test
    void assertionFailureFailsTheStepAndSkipsTheRestWhenStopping() {
        Project project = project(get("a", "/a"), get("b", "/b"), get("c", "/c"));
        Fake fake = new Fake(reply(500, "{}"));
        ScenarioItem flow = scenario(OnFailure.STOP, step("s1", "a", null, List.of(status("200"))),
                plain("s2", "b"), plain("s3", "c"));

        ScenarioResult result = run(fake, project, flow);

        assertEquals(ScenarioResult.State.FAILED, result.state());
        assertEquals(List.of(StepResult.State.FAILED, StepResult.State.SKIPPED, StepResult.State.SKIPPED),
                states(result));
        assertEquals(1, fake.sent.size());
        assertTrue(result.message().startsWith("Step \"s1\" failed"));
        assertFalse(result.steps().get(0).message().isEmpty());
        assertFalse(result.steps().get(0).assertions().get(0).passed());
    }

    @Test
    void continuingRunsRemainingStepsButTheRunStillFails() {
        Project project = project(get("a", "/a"), get("b", "/b"));
        Fake fake = new Fake(reply(500, "{}"), reply(200, "{}"));
        ScenarioItem flow = scenario(OnFailure.CONTINUE, step("s1", "a", null, List.of(status("200"))),
                plain("s2", "b"));

        ScenarioResult result = run(fake, project, flow);

        assertEquals(ScenarioResult.State.FAILED, result.state());
        assertEquals(List.of(StepResult.State.FAILED, StepResult.State.PASSED), states(result));
        assertEquals(2, fake.sent.size());
    }

    @Test
    void stepPolicyOverridesTheScenarioPolicy() {
        Project project = project(get("a", "/a"), get("b", "/b"));
        Step lenient = new Step("s1", "a", "s1", null, List.of(status("200")), 0, OnFailure.CONTINUE, 0, 0);
        ScenarioResult stopping = run(new Fake(reply(500, "{}")), project,
                scenario(OnFailure.STOP, lenient, plain("s2", "b")));
        assertEquals(List.of(StepResult.State.FAILED, StepResult.State.PASSED), states(stopping));

        Step strict = new Step("s1", "a", "s1", null, List.of(status("200")), 0, OnFailure.STOP, 0, 0);
        ScenarioResult continuing = run(new Fake(reply(500, "{}")), project,
                scenario(OnFailure.CONTINUE, strict, plain("s2", "b")));
        assertEquals(List.of(StepResult.State.FAILED, StepResult.State.SKIPPED), states(continuing));
    }

    @Test
    void nonSuccessStatusAloneIsNotAFailure() {
        ScenarioResult result = run(new Fake(reply(500, "oops")), project(get("a", "/a")),
                scenario(OnFailure.STOP, plain("s1", "a")));
        assertEquals(ScenarioResult.State.PASSED, result.state());
        assertEquals(500, result.steps().get(0).status());
    }

    @Test
    void failedExtractorFailsTheStep() {
        Project project = project(get("a", "/a"), get("b", "/b"));
        ScenarioItem flow = scenario(OnFailure.STOP, step("s1", "a", List.of(json("token", "$.missing")), null),
                plain("s2", "b"));

        ScenarioResult result = run(new Fake(reply(200, "{\"x\":1}")), project, flow);

        assertEquals(List.of(StepResult.State.FAILED, StepResult.State.SKIPPED), states(result));
        assertTrue(result.steps().get(0).message().startsWith("Extract token:"));
        assertFalse(result.steps().get(0).extractions().get(0).success());
    }

    @Test
    void unresolvedVariableFailsTheStepWithoutSending() {
        Fake fake = new Fake();
        ScenarioResult result = run(fake, project(get("a", "/a/${missing}")),
                scenario(OnFailure.STOP, plain("s1", "a")));

        assertEquals(ScenarioResult.State.FAILED, result.state());
        assertTrue(fake.sent.isEmpty());
        assertTrue(result.steps().get(0).message().contains("missing"));
        assertNull(result.steps().get(0).exchange());
    }

    @Test
    void unknownOrMissingRequestFailsTheStep() {
        Fake fake = new Fake();
        ScenarioResult result = run(fake, project(get("a", "/a")), scenario(OnFailure.CONTINUE,
                plain("s1", "nope"), plain("s2", "")));

        assertEquals(List.of(StepResult.State.FAILED, StepResult.State.FAILED), states(result));
        assertTrue(result.steps().get(0).message().contains("Unknown request"));
        assertEquals("Step has no request", result.steps().get(1).message());
        assertTrue(fake.sent.isEmpty());
    }

    @Test
    void transportFailureIsReportedOnTheStep() {
        Fake fake = new Fake(new SendOutcome.Failed(FailureKind.TIMEOUT, "timed out"));
        ScenarioResult result = run(fake, project(get("a", "/a")), scenario(OnFailure.STOP, plain("s1", "a")));

        assertEquals(ScenarioResult.State.FAILED, result.state());
        assertEquals("timed out", result.steps().get(0).message());
        assertTrue(result.message().contains("timed out"));
    }

    @Test
    void exceptionFromTheSenderFailsTheStep() {
        StepSender broken = (request, token) -> {
            throw new IllegalStateException("boom");
        };
        ScenarioResult result = new ScenarioRunner(broken).run(project(get("a", "/a")), "",
                scenario(OnFailure.STOP, plain("s1", "a")), new CancelToken());

        assertEquals(ScenarioResult.State.FAILED, result.state());
        assertEquals("boom", result.steps().get(0).message());
    }

    @Test
    void cancelledOutcomeCancelsTheRunAndSkipsTheRest() {
        Project project = project(get("a", "/a"), get("b", "/b"), get("c", "/c"));
        Fake fake = new Fake(reply(200, "{}"), new SendOutcome.Cancelled());

        ScenarioResult result = run(fake, project,
                scenario(OnFailure.CONTINUE, plain("s1", "a"), plain("s2", "b"), plain("s3", "c")));

        assertEquals(ScenarioResult.State.CANCELLED, result.state());
        assertEquals(List.of(StepResult.State.PASSED, StepResult.State.CANCELLED, StepResult.State.SKIPPED),
                states(result));
    }

    @Test
    void tokenCancelledBetweenStepsStopsBeforeTheNextRequest() {
        Project project = project(get("a", "/a"), get("b", "/b"), get("c", "/c"));
        CancelToken token = new CancelToken();
        Fake fake = new Fake(reply(200, "{}"));
        fake.afterFirst = token::cancel;

        ScenarioResult result = new ScenarioRunner(fake).run(project, "",
                scenario(OnFailure.STOP, plain("s1", "a"), plain("s2", "b"), plain("s3", "c")), token);

        assertEquals(ScenarioResult.State.CANCELLED, result.state());
        assertEquals(1, fake.sent.size());
        assertEquals(List.of(StepResult.State.PASSED, StepResult.State.CANCELLED, StepResult.State.SKIPPED),
                states(result));
    }

    @Test
    void delayIsSleptInSlices() {
        List<Long> slept = new ArrayList<>();
        Step delayed = new Step("s1", "a", "s1", null, null, 120, null, 0, 0);
        ScenarioResult result = new ScenarioRunner(new Fake(), slept::add).run(project(get("a", "/a")), "",
                scenario(OnFailure.STOP, delayed), new CancelToken());

        assertEquals(ScenarioResult.State.PASSED, result.state());
        assertEquals(List.of(50L, 50L, 20L), slept);
    }

    @Test
    void cancellingDuringADelayStopsBeforeSending() {
        CancelToken token = new CancelToken();
        Fake fake = new Fake();
        Step delayed = new Step("s1", "a", "s1", null, null, 500, null, 0, 0);
        ScenarioResult result = new ScenarioRunner(fake, millis -> token.cancel()).run(project(get("a", "/a")), "",
                scenario(OnFailure.STOP, delayed), token);

        assertEquals(ScenarioResult.State.CANCELLED, result.state());
        assertTrue(fake.sent.isEmpty());
    }

    @Test
    void scenarioVariablesAreUsedAndExtractedValuesWinOverThem() {
        Project project = project(get("a", "/a/${email}"), get("b", "/b/${email}"));
        ScenarioItem flow = new ScenarioItem("sc", "Flow", List.of(Variable.of("email", "declared@x.io")),
                List.of(step("s1", "a", List.of(json("email", "$.email")), null), plain("s2", "b")), null, null);
        Fake fake = new Fake(reply(200, "{\"email\":\"extracted@x.io\"}"), reply(200, "{}"));

        ScenarioResult result = run(fake, project, flow);

        assertEquals(ScenarioResult.State.PASSED, result.state(), result.message());
        assertEquals(BASE + "/a/declared@x.io", fake.sent.get(0).url());
        assertEquals(BASE + "/b/extracted@x.io", fake.sent.get(1).url());
    }

    @Test
    void laterExtractionOverwritesAnEarlierOneWithTheSameName() {
        Project project = project(get("a", "/a"), get("b", "/b"), get("c", "/c/${id}"));
        Fake fake = new Fake(reply(200, "{\"id\":\"first\"}"), reply(200, "{\"id\":\"second\"}"), reply(200, "{}"));
        ScenarioItem flow = scenario(OnFailure.STOP, step("s1", "a", List.of(json("id", "$.id")), null),
                step("s2", "b", List.of(json("id", "$.id")), null), plain("s3", "c"));

        run(fake, project, flow);

        assertEquals(BASE + "/c/second", fake.sent.get(2).url());
    }

    @Test
    void expectedValueInAssertionResolvesVariables() {
        Project project = project(get("a", "/a"), get("b", "/b"));
        Fake fake = new Fake(reply(200, "{\"id\":\"u1\"}"), reply(200, "{\"owner\":\"u1\"}"));
        ScenarioItem flow = scenario(OnFailure.STOP, step("s1", "a", List.of(json("userId", "$.id")), null),
                step("s2", "b", null, List.of(jsonEquals("$.owner", "${userId}"))));

        ScenarioResult result = run(fake, project, flow);

        assertEquals(ScenarioResult.State.PASSED, result.state(), result.message());
    }

    @Test
    void dynamicVariablesResolveInRequests() {
        Fake fake = new Fake();
        run(fake, project(get("a", "/items/${$uuid}")), scenario(OnFailure.STOP, plain("s1", "a")));

        assertTrue(fake.sent.get(0).url().matches(".*/items/[0-9a-f-]{36}"), fake.sent.get(0).url());
    }

    @Test
    void edgesDecideTheExecutionOrder() {
        Project project = project(get("a", "/a"), get("b", "/b"));
        ScenarioItem flow = new ScenarioItem("sc", "Flow", null, List.of(plain("s1", "a"), plain("s2", "b")),
                List.of(new Edge("s2", "s1")), null);
        Fake fake = new Fake();

        ScenarioResult result = run(fake, project, flow);

        assertEquals(List.of("s2", "s1"), result.steps().stream().map(r -> r.step().id()).toList());
        assertEquals(BASE + "/b", fake.sent.get(0).url());
    }

    @Test
    void invalidChainIsReportedWithoutSending() {
        Project project = project(get("a", "/a"), get("b", "/b"));
        ScenarioItem flow = new ScenarioItem("sc", "Flow", null, List.of(plain("s1", "a"), plain("s2", "b")),
                List.of(new Edge("s1", "zzz")), null);
        Fake fake = new Fake();
        List<String> events = new ArrayList<>();

        ScenarioResult result = new ScenarioRunner(fake).run(project, "", flow, new CancelToken(),
                new ScenarioListener() {
                    @Override
                    public void onStarted(List<Step> steps) {
                        events.add("started");
                    }

                    @Override
                    public void onFinished(ScenarioResult finished) {
                        events.add("finished:" + finished.state());
                    }
                });

        assertEquals(ScenarioResult.State.INVALID, result.state());
        assertFalse(result.message().isEmpty());
        assertTrue(fake.sent.isEmpty());
        assertEquals(List.of("finished:INVALID"), events);
    }

    @Test
    void listenerSeesProgressInOrder() {
        Project project = project(get("a", "/a"), get("b", "/b"));
        List<String> events = new ArrayList<>();
        ScenarioListener listener = new ScenarioListener() {
            @Override
            public void onStarted(List<Step> steps) {
                events.add("started:" + steps.size());
            }

            @Override
            public void onStepStarted(Step step) {
                events.add("running:" + step.id());
            }

            @Override
            public void onStepFinished(StepResult result) {
                events.add(result.step().id() + ":" + result.state());
            }

            @Override
            public void onFinished(ScenarioResult result) {
                events.add("finished:" + result.state());
            }
        };

        new ScenarioRunner(new Fake(reply(500, "{}"))).run(project, "",
                scenario(OnFailure.STOP, step("s1", "a", null, List.of(status("200"))), plain("s2", "b")),
                new CancelToken(), listener);

        assertEquals(List.of("started:2", "running:s1", "s1:FAILED", "s2:SKIPPED", "finished:FAILED"), events);
    }

    @Test
    void stepResultKeepsTheExchangeAndTimings() {
        ScenarioResult result = run(new Fake(reply(201, "{\"id\":\"x\"}")), project(get("a", "/a")),
                scenario(OnFailure.STOP, step("s1", "a", List.of(json("id", "$.id")), List.of(status("201")))));

        StepResult step = result.steps().get(0);
        assertNotNull(step.exchange());
        assertEquals(201, step.status());
        assertEquals("x", step.extractions().get(0).value());
        assertTrue(step.assertions().get(0).passed());
        assertTrue(result.millis() >= 0);
    }

    private static String header(PreparedRequest request, String name) {
        return request.headers().stream().filter(h -> h.key().equalsIgnoreCase(name)).map(KeyValue::value)
                .findFirst().orElse(null);
    }
}
