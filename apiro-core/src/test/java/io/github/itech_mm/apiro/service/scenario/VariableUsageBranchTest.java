package io.github.itech_mm.apiro.service.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.BranchRule;
import io.github.itech_mm.apiro.model.Edge;
import io.github.itech_mm.apiro.model.EdgeCondition;
import io.github.itech_mm.apiro.model.Extractor;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariableUsageBranchTest {

    private static RequestItem get(String id, String url) {
        return new RequestItem(id, id, HttpMethod.GET, url, null, null, null, null, null);
    }

    private static Step step(String id, String requestId, String... extracted) {
        List<Extractor> extractors = new java.util.ArrayList<>();
        for (String name : extracted) {
            extractors.add(new Extractor(name, Extractor.Source.JSON_PATH, "$." + name));
        }
        return new Step(id, requestId, id, extractors, null, 0, null, 0, 0);
    }

    @Test
    void siblingBranchesDoNotLeakExtractedVariablesToEachOther() {
        Project project = new Project(null, 0, "p", "P", null, null, null, null, null, null,
                List.of(get("r1", "http://h"), get("r2", "http://h"), get("r3", "http://h/${secret}")));

        ScenarioItem scenario = new ScenarioItem("sc", "Flow", null,
                List.of(step("a", "r1"), step("b", "r2", "secret"), step("c", "r3")),
                List.of(
                        Edge.of("a", "b", EdgeCondition.all(BranchRule.status(BranchRule.Operator.EQUALS, "200")), "b"),
                        Edge.of("a", "c", EdgeCondition.ELSE, "c")),
                null);

        VariableUsage.Result result = VariableUsage.analyze(project, "", scenario);
        assertEquals(List.of("secret"), result.unresolvedFor("c"));
        assertTrue(result.links().isEmpty());
    }

    @Test
    void diamondMergeResolvesVariableExtractedOnEitherIncomingBranch() {
        Project project = new Project(null, 0, "p", "P", null, null, null, null, null, null,
                List.of(get("r1", "http://h"), get("r2", "http://h"), get("r3", "http://h"),
                        get("r4", "http://h/orders/${orderId}")));

        ScenarioItem scenario = new ScenarioItem("sc", "Flow", null,
                List.of(step("a", "r1"), step("b", "r2", "orderId"), step("c", "r3", "orderId"), step("d", "r4")),
                List.of(
                        Edge.of("a", "b", EdgeCondition.all(BranchRule.status(BranchRule.Operator.EQUALS, "200")), "b"),
                        Edge.of("a", "c", EdgeCondition.ELSE, "c"),
                        Edge.of("b", "d"),
                        Edge.of("c", "d")),
                null);

        VariableUsage.Result result = VariableUsage.analyze(project, "", scenario);
        assertTrue(result.unresolved().isEmpty());
        assertEquals(2, result.links().size());
    }

    @Test
    void branchRulesReportUnresolvedVariablesWhenMissing() {
        Project project = new Project(null, 0, "p", "P", null, null, null, null, null, null,
                List.of(get("r1", "http://h"), get("r2", "http://h")));
        Edge edge = new Edge("e1", "a", "b", EdgeCondition.all(
                BranchRule.variable("role", BranchRule.Operator.EQUALS, "${expectedRole}")), "check");
        ScenarioItem scenario = new ScenarioItem("sc", "Flow", null,
                List.of(step("a", "r1", "role"), step("b", "r2")),
                List.of(edge), null);

        VariableUsage.Result result = VariableUsage.analyze(project, "", scenario);
        assertEquals(List.of("expectedRole"), result.unresolvedForEdge("e1"));
    }
}
