package io.github.itech_mm.apiro.service.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.BranchRule;
import io.github.itech_mm.apiro.model.EdgeCondition;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.ResponseData;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.model.Variable;
import io.github.itech_mm.apiro.service.http.ExchangeResult;
import io.github.itech_mm.apiro.service.variables.VariableResolver;
import java.util.List;
import org.junit.jupiter.api.Test;

class BranchEvaluatorTest {

    private static StepResult done(int status, long millis, String body, List<KeyValue> headers,
                                   StepResult.State state) {
        ResponseData response = new ResponseData(status, "", millis, 1, body.length(), 1, headers, null, body);
        return new StepResult(Step.of("r1", "Check"), state, "", millis,
                new ExchangeResult(response, null, null), null, null);
    }

    @Test
    void alwaysAndElseMatch() {
        StepResult passed = done(200, 10, "{}", List.of(), StepResult.State.PASSED);
        assertTrue(BranchEvaluator.evaluate(EdgeCondition.ALWAYS, passed, null).matched());
        assertTrue(BranchEvaluator.evaluate(EdgeCondition.ELSE, passed, null).matched());
    }

    @Test
    void statusJsonHeaderTimeAndBodyRulesEvaluate() {
        StepResult result = done(201, 240,
                "{\"user\":{\"role\":\"ADMIN\",\"tags\":[\"vip\",\"beta\"],\"emptyList\":[],\"note\":\"\"}}",
                List.of(KeyValue.of("X-Region", "eu-west-1")), StepResult.State.PASSED);
        VariableResolver resolver = VariableResolver.of(List.of(List.of(Variable.of("wantedRole", "ADMIN"))));

        EdgeCondition all = EdgeCondition.all(
                BranchRule.status(BranchRule.Operator.EQUALS, "201"),
                BranchRule.jsonPath("$.user.role", BranchRule.Operator.EQUALS, "${wantedRole}"),
                BranchRule.jsonPath("$.user.tags", BranchRule.Operator.CONTAINS, "vip"),
                BranchRule.jsonPath("$.user.emptyList", BranchRule.Operator.IS_EMPTY, ""),
                BranchRule.jsonPath("$.user.note", BranchRule.Operator.IS_EMPTY, ""),
                BranchRule.jsonPath("$.user.tags", BranchRule.Operator.NOT_EMPTY, ""),
                BranchRule.header("X-Region", BranchRule.Operator.MATCHES_REGEX, "eu-.*"),
                BranchRule.of(BranchRule.Source.RESPONSE_TIME, "", BranchRule.Operator.LESS_THAN, "500"),
                BranchRule.of(BranchRule.Source.BODY, "", BranchRule.Operator.CONTAINS, "ADMIN"));

        BranchEvaluator.Evaluation evaluation = BranchEvaluator.evaluate(all, result, resolver);
        assertTrue(evaluation.matched());
        assertEquals(9, evaluation.rules().size());
    }

    @Test
    void anyMatchesWhenAtLeastOneRulePasses() {
        StepResult result = done(409, 15, "{\"code\":\"DUPLICATE\"}", List.of(), StepResult.State.PASSED);
        EdgeCondition any = EdgeCondition.any(
                BranchRule.status(BranchRule.Operator.EQUALS, "201"),
                BranchRule.status(BranchRule.Operator.EQUALS, "409"));

        assertTrue(BranchEvaluator.evaluate(any, result, null).matched());

        EdgeCondition none = EdgeCondition.any(
                BranchRule.status(BranchRule.Operator.EQUALS, "201"),
                BranchRule.status(BranchRule.Operator.EQUALS, "404"));
        assertFalse(BranchEvaluator.evaluate(none, result, null).matched());
    }

    @Test
    void variableAndStepOutcomeWorkWithoutHttpExchange() {
        StepResult failed = StepResult.of(Step.of("r1", "Pay"), StepResult.State.FAILED, "timed out");
        VariableResolver resolver = VariableResolver.of(List.of(List.of(Variable.of("retryMode", "fallback"))));

        EdgeCondition recovery = EdgeCondition.all(
                BranchRule.stepFailed(),
                BranchRule.variable("retryMode", BranchRule.Operator.EQUALS, "fallback"),
                BranchRule.variable("missingVar", BranchRule.Operator.NOT_EXISTS, ""));

        assertTrue(BranchEvaluator.evaluate(recovery, failed, resolver).matched());
        assertTrue(BranchEvaluator.usesStepOutcome(recovery));

        EdgeCondition httpCheck = EdgeCondition.all(BranchRule.status(BranchRule.Operator.NOT_EXISTS, ""));
        BranchEvaluator.Evaluation missingExchange = BranchEvaluator.evaluate(httpCheck, failed, resolver);
        assertFalse(missingExchange.matched());
        assertEquals("No HTTP response available", missingExchange.rules().get(0).message());
    }

    @Test
    void invalidRegexFailsSafelyWithoutThrowing() {
        StepResult result = done(200, 10, "{\"id\":\"a1\"}", List.of(), StepResult.State.PASSED);
        EdgeCondition badRegex = EdgeCondition.all(
                BranchRule.jsonPath("$.id", BranchRule.Operator.MATCHES_REGEX, "[unclosed"));

        BranchEvaluator.Evaluation evaluation = BranchEvaluator.evaluate(badRegex, result, null);
        assertFalse(evaluation.matched());
        assertTrue(evaluation.rules().get(0).message().contains("Invalid regex"));
    }
}
