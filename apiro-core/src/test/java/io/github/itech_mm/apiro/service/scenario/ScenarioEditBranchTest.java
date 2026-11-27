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
import org.junit.jupiter.api.Test;

class ScenarioEditBranchTest {

    private static ScenarioItem three() {
        return new ScenarioItem("sc", "Flow", null,
                List.of(new Step("a", "r1", "A", null, null, 0, null, 0, 0),
                        new Step("b", "r2", "B", null, null, 0, null, 0, 0),
                        new Step("c", "r3", "C", null, null, 0, null, 0, 0)),
                null, null);
    }

    @Test
    void materializeConvertsImplicitLinearChainToEdges() {
        ScenarioItem next = ScenarioEdit.withMaterializedEdges(three());
        assertEquals(2, next.edges().size());
        assertEquals("a", next.edges().get(0).from());
        assertEquals("b", next.edges().get(0).to());
        assertFalse(next.edges().get(0).id().isBlank());
    }

    @Test
    void edgeCrudAndPriorityReorderPreserveOtherEdges() {
        Edge e1 = new Edge("e1", "a", "b", EdgeCondition.all(BranchRule.status(BranchRule.Operator.EQUALS, "200")), "ok");
        Edge other = new Edge("eo", "b", "c", EdgeCondition.ALWAYS, "");
        Edge e2 = new Edge("e2", "a", "c", EdgeCondition.ELSE, "else");

        ScenarioItem flow = ScenarioEdit.withEdge(three(), e1);
        flow = ScenarioEdit.withEdge(flow, other);
        flow = ScenarioEdit.withEdge(flow, e2);

        ScenarioItem reordered = ScenarioEdit.withMovedEdge(flow, "a", "e2", 0);
        assertEquals(List.of("e2", "eo", "e1"), reordered.edges().stream().map(Edge::id).toList());

        Edge updated = new Edge("e1", "a", "b", EdgeCondition.all(BranchRule.status(BranchRule.Operator.EQUALS, "201")),
                "created");
        ScenarioItem edited = ScenarioEdit.withEdge(reordered, updated);
        assertEquals("created", edited.edges().get(2).label());

        ScenarioItem removed = ScenarioEdit.withoutEdge(edited, "e2");
        assertEquals(List.of("eo", "e1"), removed.edges().stream().map(Edge::id).toList());
    }

    @Test
    void deletingAStepInBranchedScenarioDropsOnlyIncidentEdges() {
        Edge e1 = new Edge("e1", "a", "b", EdgeCondition.all(BranchRule.status(BranchRule.Operator.EQUALS, "200")), "");
        Edge e2 = new Edge("e2", "a", "c", EdgeCondition.ELSE, "");
        Edge e3 = new Edge("e3", "b", "c", EdgeCondition.ALWAYS, "");
        ScenarioItem flow = new ScenarioItem("sc", "Flow", null, three().steps(), List.of(e1, e2, e3), null);

        ScenarioItem pruned = ScenarioEdit.withoutStep(flow, "b");
        assertEquals(List.of("e2"), pruned.edges().stream().map(Edge::id).toList());
        assertTrue(ScenarioGraph.plan(pruned).valid());
    }
}
