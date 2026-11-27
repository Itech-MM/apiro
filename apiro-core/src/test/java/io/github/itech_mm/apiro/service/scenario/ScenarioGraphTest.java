package io.github.itech_mm.apiro.service.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.BranchRule;
import io.github.itech_mm.apiro.model.Edge;
import io.github.itech_mm.apiro.model.EdgeCondition;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScenarioGraphTest {

    private static Step step(String id) {
        return new Step(id, "r-" + id, id, null, null, 0, null, 0, 0);
    }

    private static ScenarioItem scenario(List<Step> steps, Edge... edges) {
        return new ScenarioItem("sc", "Flow", null, steps, List.of(edges), null);
    }

    @Test
    void supportsConditionalBranchesAndDiamondMerges() {
        Edge e1 = Edge.of("a", "b", EdgeCondition.all(BranchRule.status(BranchRule.Operator.EQUALS, "200")), "ok");
        Edge e2 = Edge.of("a", "c", EdgeCondition.ELSE, "else");
        Edge e3 = Edge.of("b", "d");
        Edge e4 = Edge.of("c", "d");

        ScenarioGraph.Plan plan = ScenarioGraph.plan(scenario(List.of(step("d"), step("b"), step("c"), step("a")),
                e1, e2, e3, e4));

        assertTrue(plan.valid(), plan.problem());
        assertEquals("a", plan.root().id());
        assertEquals(Set.of("a", "b", "c"), plan.ancestorsOf("d"));
        assertEquals(Set.of("b", "c", "d"), plan.reachableAfter("a"));
        assertTrue(plan.isBranched());
    }

    @Test
    void rejectsMultipleElseBranchesOnSameSource() {
        Edge e1 = Edge.of("a", "b", EdgeCondition.ELSE, "else1");
        Edge e2 = Edge.of("a", "c", EdgeCondition.ELSE, "else2");

        ScenarioGraph.Plan plan = ScenarioGraph.plan(scenario(List.of(step("a"), step("b"), step("c")), e1, e2));

        assertFalse(plan.valid());
        assertTrue(plan.problem().contains("Else"));
    }

    @Test
    void rejectsAlwaysMixedWithOtherBranchesOnSameSource() {
        Edge e1 = Edge.of("a", "b");
        Edge e2 = Edge.of("a", "c", EdgeCondition.all(BranchRule.status(BranchRule.Operator.EQUALS, "404")), "404");

        ScenarioGraph.Plan plan = ScenarioGraph.plan(scenario(List.of(step("a"), step("b"), step("c")), e1, e2));

        assertFalse(plan.valid());
        assertTrue(plan.problem().contains("unconditional"));
    }

    @Test
    void rejectsCyclesAndDetachedComponents() {
        ScenarioGraph.Plan cycle = ScenarioGraph.plan(scenario(List.of(step("a"), step("b")),
                Edge.of("a", "b"), Edge.of("b", "a")));
        assertFalse(cycle.valid());
        assertTrue(cycle.problem().contains("loop"));

        ScenarioGraph.Plan detached = ScenarioGraph.plan(scenario(List.of(step("a"), step("b"), step("c")),
                Edge.of("a", "b")));
        assertFalse(detached.valid());
        assertTrue(detached.problem().contains("not connected"));
    }
}
