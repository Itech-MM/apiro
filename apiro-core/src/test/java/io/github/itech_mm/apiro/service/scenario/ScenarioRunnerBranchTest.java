package io.github.itech_mm.apiro.service.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_framework.api_client.utils.CancelToken;
import io.github.itech_mm.apiro.model.Assertion;
import io.github.itech_mm.apiro.model.BranchRule;
import io.github.itech_mm.apiro.model.Edge;
import io.github.itech_mm.apiro.model.EdgeCondition;
import io.github.itech_mm.apiro.model.Extractor;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.OnFailure;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ResponseData;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.service.http.ExchangeResult;
import io.github.itech_mm.apiro.service.http.FailureKind;
import io.github.itech_mm.apiro.service.http.RequestPreparer.PreparedRequest;
import io.github.itech_mm.apiro.service.http.SendOutcome;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ScenarioRunnerBranchTest {

    private static final String BASE = "http://api.test";

    private static final class Fake implements StepSender {

        final Deque<SendOutcome> outcomes = new ArrayDeque<>();
        final List<PreparedRequest> sent = new ArrayList<>();

        Fake(SendOutcome... scripted) {
            outcomes.addAll(List.of(scripted));
        }

        @Override
        public SendOutcome send(PreparedRequest request, CancelToken token) {
            sent.add(request);
            return outcomes.isEmpty() ? reply(200, "{}") : outcomes.poll();
        }
    }

    private static SendOutcome reply(int status, String body) {
        ResponseData response = new ResponseData(status, "", 5, 2, body.length(), 1, List.of(), null, body);
        return new SendOutcome.Done(new ExchangeResult(response, null, null));
    }

    private static RequestItem get(String id, String path) {
        return new RequestItem(id, id, HttpMethod.GET, BASE + path, null, null, null, null, null);
    }

    private static Project project(RequestItem... requests) {
        return new Project(null, 0, "p", "Shop", null, null, null, null, null, null, List.of(requests));
    }

    private static Step step(String id, String requestId, List<Extractor> extractors, List<Assertion> assertions) {
        return new Step(id, requestId, id, extractors, assertions, 0, null, 0, 0);
    }

    private static Map<String, StepResult.State> byStep(ScenarioResult result) {
        return result.steps().stream().collect(Collectors.toMap(r -> r.step().id(), StepResult::state));
    }

    @Test
    void routesThroughIfElseIfElseAndMergesIntoFinalStep() {
        Project project = project(
                get("check", "/users/1"),
                get("update", "/users/1/refresh"),
                get("create", "/users"),
                get("fallback", "/users/fallback"),
                get("summary", "/orders/${targetId}"));

        Step sCheck = step("check", "check", null, null);
        Step sUpdate = step("update", "update",
                List.of(new Extractor("targetId", Extractor.Source.JSON_PATH, "$.id")), null);
        Step sCreate = step("create", "create",
                List.of(new Extractor("targetId", Extractor.Source.JSON_PATH, "$.id")), null);
        Step sFallback = step("fallback", "fallback",
                List.of(new Extractor("targetId", Extractor.Source.JSON_PATH, "$.id")), null);
        Step sSummary = step("summary", "summary", null, null);

        ScenarioItem flow = new ScenarioItem("sc", "BranchMerge", null,
                List.of(sCheck, sUpdate, sCreate, sFallback, sSummary),
                List.of(
                        Edge.of("check", "update", EdgeCondition.all(BranchRule.status(BranchRule.Operator.EQUALS, "200")),
                                "exists"),
                        Edge.of("check", "create", EdgeCondition.all(BranchRule.status(BranchRule.Operator.EQUALS, "404")),
                                "missing"),
                        Edge.of("check", "fallback", EdgeCondition.ELSE, "other"),
                        Edge.of("update", "summary"),
                        Edge.of("create", "summary"),
                        Edge.of("fallback", "summary")),
                OnFailure.STOP);

        Fake fake = new Fake(
                reply(404, "{}"),
                reply(201, "{\"id\":\"u-new\"}"),
                reply(200, "{\"ok\":true}"));

        ScenarioResult result = new ScenarioRunner(fake).run(project, "", flow, new CancelToken());

        assertEquals(ScenarioResult.State.PASSED, result.state(), result.message());
        assertEquals(3, fake.sent.size());
        assertEquals(BASE + "/orders/u-new", fake.sent.get(2).url());
        Map<String, StepResult.State> states = byStep(result);
        assertEquals(StepResult.State.PASSED, states.get("check"));
        assertEquals(StepResult.State.PASSED, states.get("create"));
        assertEquals(StepResult.State.PASSED, states.get("summary"));
        assertEquals(StepResult.State.SKIPPED, states.get("update"));
        assertEquals(StepResult.State.SKIPPED, states.get("fallback"));

        List<String> lines = RunLog.stepLines(result.steps().get(0), "check");
        assertTrue(lines.stream().anyMatch(line -> line.contains("branch -> create (missing)")));
    }

    @Test
    void unmatchedOptionalConditionalBranchEndsCleanlyAsSkipped() {
        Project project = project(get("login", "/login"), get("mfa", "/mfa"));
        Step sLogin = step("login", "login", null, null);
        Step sMfa = step("mfa", "mfa", null, null);

        ScenarioItem flow = new ScenarioItem("sc", "OptionalMfa", null,
                List.of(sLogin, sMfa),
                List.of(Edge.of("login", "mfa",
                        EdgeCondition.all(BranchRule.jsonPath("$.mfaRequired", BranchRule.Operator.EQUALS, "true")),
                        "mfa")),
                OnFailure.STOP);

        Fake fake = new Fake(reply(200, "{\"mfaRequired\":false}"));
        ScenarioResult result = new ScenarioRunner(fake).run(project, "", flow, new CancelToken());

        assertEquals(ScenarioResult.State.PASSED, result.state());
        assertEquals(1, fake.sent.size());
        assertEquals(StepResult.State.SKIPPED, result.steps().get(1).state());
        assertEquals("Branch not taken", result.steps().get(1).message());
    }

    @Test
    void stepOutcomeRecoveryBranchCatchesFailureAndPassesScenario() {
        Project project = project(get("primary", "/primary"), get("normal", "/normal"), get("recover", "/recover"));
        Step sPrimary = step("primary", "primary", null,
                List.of(new Assertion(Assertion.Source.STATUS, "", Assertion.Operator.EQUALS, "200")));
        Step sNormal = step("normal", "normal", null, null);
        Step sRecover = step("recover", "recover", null,
                List.of(new Assertion(Assertion.Source.STATUS, "", Assertion.Operator.EQUALS, "200")));

        ScenarioItem flow = new ScenarioItem("sc", "Recovery", null,
                List.of(sPrimary, sNormal, sRecover),
                List.of(
                        Edge.of("primary", "normal", EdgeCondition.all(BranchRule.stepPassed()), "ok"),
                        Edge.of("primary", "recover", EdgeCondition.all(BranchRule.stepFailed()), "recover")),
                OnFailure.STOP);

        Fake fake = new Fake(new SendOutcome.Failed(FailureKind.TIMEOUT, "timeout"), reply(200, "{}"));
        ScenarioResult result = new ScenarioRunner(fake).run(project, "", flow, new CancelToken());

        assertEquals(ScenarioResult.State.PASSED, result.state(), result.message());
        Map<String, StepResult.State> states = byStep(result);
        assertEquals(StepResult.State.FAILED, states.get("primary"));
        assertEquals(StepResult.State.PASSED, states.get("recover"));
        assertEquals(StepResult.State.SKIPPED, states.get("normal"));
    }

    @Test
    void stopOnFailureSkipsElseBranchWhenNoRecoveryRuleExists() {
        Project project = project(get("check", "/check"), get("ok", "/ok"), get("fallback", "/fallback"));
        Step sCheck = step("check", "check", null,
                List.of(new Assertion(Assertion.Source.STATUS, "", Assertion.Operator.EQUALS, "200")));
        Step sOk = step("ok", "ok", null, null);
        Step sFallback = step("fallback", "fallback", null, null);

        ScenarioItem flow = new ScenarioItem("sc", "Strict", null,
                List.of(sCheck, sOk, sFallback),
                List.of(
                        Edge.of("check", "ok", EdgeCondition.all(BranchRule.status(BranchRule.Operator.EQUALS, "200")),
                                "ok"),
                        Edge.of("check", "fallback", EdgeCondition.ELSE, "else")),
                OnFailure.STOP);

        Fake fake = new Fake(reply(500, "{}"));
        ScenarioResult result = new ScenarioRunner(fake).run(project, "", flow, new CancelToken());

        assertEquals(ScenarioResult.State.FAILED, result.state());
        assertEquals(1, fake.sent.size());
        assertEquals(StepResult.State.SKIPPED, byStep(result).get("ok"));
        assertEquals(StepResult.State.SKIPPED, byStep(result).get("fallback"));
    }
}
