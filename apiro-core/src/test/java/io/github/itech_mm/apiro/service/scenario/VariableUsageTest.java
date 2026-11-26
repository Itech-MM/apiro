package io.github.itech_mm.apiro.service.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.Extractor;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.model.Variable;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariableUsageTest {

    private static RequestItem request(String id, String url, AuthConfig auth, List<KeyValue> headers) {
        return new RequestItem(id, id, HttpMethod.GET, url, null, headers, auth, null, null);
    }

    private static Step step(String id, String requestId, String... extracted) {
        List<Extractor> extractors = new java.util.ArrayList<>();
        for (String variable : extracted) {
            extractors.add(new Extractor(variable, Extractor.Source.JSON_PATH, "$." + variable));
        }
        return new Step(id, requestId, id, extractors, null, 0, null, 0, 0);
    }

    private static Project project(List<Variable> variables, RequestItem... requests) {
        return new Project(null, 0, "p", "P", null, null, null, null, variables, null, List.of(requests));
    }

    @Test
    void linksTheStepThatExtractedTheVariable() {
        Project project = project(null, request("login", "http://h/login", null, null),
                request("orders", "http://h/orders/${userId}", AuthConfig.bearer("${token}"), null));
        ScenarioItem scenario = new ScenarioItem("sc", "Flow", null,
                List.of(step("a", "login", "token", "userId"), step("b", "orders")), null, null);

        VariableUsage.Result result = VariableUsage.analyze(project, "", scenario);

        assertEquals(1, result.links().size());
        assertEquals("a", result.links().get(0).fromStepId());
        assertEquals("b", result.links().get(0).toStepId());
        assertEquals(List.of("userId", "token"), result.links().get(0).names());
        assertTrue(result.unresolved().isEmpty());
    }

    @Test
    void latestEarlierProducerWins() {
        Project project = project(null, request("r1", "http://h", null, null), request("r2", "http://h", null, null),
                request("r3", "http://h/${id}", null, null));
        ScenarioItem scenario = new ScenarioItem("sc", "Flow", null,
                List.of(step("a", "r1", "id"), step("b", "r2", "id"), step("c", "r3")), null, null);

        VariableUsage.Result result = VariableUsage.analyze(project, "", scenario);

        assertEquals(1, result.links().size());
        assertEquals("b", result.links().get(0).fromStepId());
    }

    @Test
    void laterProducerDoesNotCountAndTheNameIsUnresolved() {
        Project project = project(null, request("r1", "http://h/${id}", null, null),
                request("r2", "http://h", null, null));
        ScenarioItem scenario = new ScenarioItem("sc", "Flow", null,
                List.of(step("a", "r1"), step("b", "r2", "id")), null, null);

        VariableUsage.Result result = VariableUsage.analyze(project, "", scenario);

        assertTrue(result.links().isEmpty());
        assertEquals(List.of("id"), result.unresolvedFor("a"));
    }

    @Test
    void projectVariablesAndDynamicOnesAreNotUnresolved() {
        Project project = project(List.of(Variable.of("host", "h")),
                request("r1", "http://${host}/${$uuid}", null, List.of(KeyValue.of("X", "${missing}"))));
        ScenarioItem scenario = new ScenarioItem("sc", "Flow", null, List.of(step("a", "r1")), null, null);

        VariableUsage.Result result = VariableUsage.analyze(project, "", scenario);

        assertTrue(result.links().isEmpty());
        assertEquals(List.of("missing"), result.unresolvedFor("a"));
    }

    @Test
    void stepHeaderOverridesAreScanned() {
        Project project = project(null, request("r1", "http://h", null, null), request("r2", "http://h", null, null));
        Step second = new Step("b", "r2", "b", null, null, List.of(KeyValue.of("Authorization", "Bearer ${token}")),
                0, null, 0, 0);
        ScenarioItem scenario = new ScenarioItem("sc", "Flow", null, List.of(step("a", "r1", "token"), second), null,
                null);

        VariableUsage.Result result = VariableUsage.analyze(project, "", scenario);

        assertEquals(List.of("token"), result.links().get(0).names());
    }
}
