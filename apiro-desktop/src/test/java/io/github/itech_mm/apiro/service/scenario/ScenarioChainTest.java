package io.github.itech_mm.apiro.service.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.Edge;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioChainTest {

    private static Step step(String id) {
        return new Step(id, "r", id, null, null, 0, null, 0, 0);
    }

    private static ScenarioItem scenario(List<Step> steps, Edge... edges) {
        return new ScenarioItem("sc", "Flow", null, steps, List.of(edges), null);
    }

    private static List<String> ids(ScenarioChain.Result result) {
        return result.steps().stream().map(Step::id).toList();
    }

    @Test
    void listOrderIsUsedWhenThereAreNoEdges() {
        ScenarioChain.Result result = ScenarioChain.order(scenario(List.of(step("a"), step("b"), step("c"))));
        assertTrue(result.valid());
        assertEquals(List.of("a", "b", "c"), ids(result));
    }

    @Test
    void singleStepWithoutEdgesIsValid() {
        assertEquals(List.of("a"), ids(ScenarioChain.order(scenario(List.of(step("a"))))));
    }

    @Test
    void edgesDecideTheOrder() {
        ScenarioChain.Result result = ScenarioChain.order(
                scenario(List.of(step("a"), step("b"), step("c")), new Edge("c", "a"), new Edge("a", "b")));
        assertTrue(result.valid());
        assertEquals(List.of("c", "a", "b"), ids(result));
    }

    @Test
    void emptyScenarioIsInvalid() {
        ScenarioChain.Result result = ScenarioChain.order(scenario(List.of()));
        assertFalse(result.valid());
        assertTrue(result.steps().isEmpty());
    }

    @Test
    void duplicateStepIdsAreInvalid() {
        assertFalse(ScenarioChain.order(scenario(List.of(step("a"), step("a")))).valid());
    }

    @Test
    void edgeToMissingStepIsInvalid() {
        assertFalse(ScenarioChain.order(scenario(List.of(step("a"), step("b")), new Edge("a", "zzz"))).valid());
        assertFalse(ScenarioChain.order(scenario(List.of(step("a"), step("b")), new Edge("zzz", "b"))).valid());
    }

    @Test
    void selfConnectionIsInvalid() {
        assertFalse(ScenarioChain.order(scenario(List.of(step("a"), step("b")), new Edge("a", "a"))).valid());
    }

    @Test
    void branchIsInvalid() {
        ScenarioChain.Result result = ScenarioChain.order(
                scenario(List.of(step("a"), step("b"), step("c")), new Edge("a", "b"), new Edge("a", "c")));
        assertFalse(result.valid());
        assertTrue(result.problem().contains("branch"));
    }

    @Test
    void mergeIsInvalid() {
        ScenarioChain.Result result = ScenarioChain.order(
                scenario(List.of(step("a"), step("b"), step("c")), new Edge("a", "c"), new Edge("b", "c")));
        assertFalse(result.valid());
        assertTrue(result.problem().contains("merging"));
    }

    @Test
    void pureLoopIsInvalid() {
        ScenarioChain.Result result = ScenarioChain.order(
                scenario(List.of(step("a"), step("b")), new Edge("a", "b"), new Edge("b", "a")));
        assertFalse(result.valid());
        assertTrue(result.problem().contains("loop"));
    }

    @Test
    void unconnectedStepIsInvalid() {
        ScenarioChain.Result result = ScenarioChain.order(
                scenario(List.of(step("a"), step("b"), step("c")), new Edge("a", "b")));
        assertFalse(result.valid());
        assertTrue(result.problem().contains("one chain"));
    }

    @Test
    void detachedLoopNextToAChainIsInvalid() {
        ScenarioChain.Result result = ScenarioChain.order(scenario(
                List.of(step("a"), step("b"), step("c"), step("d")),
                new Edge("a", "b"), new Edge("c", "d"), new Edge("d", "c")));
        assertFalse(result.valid());
    }
}
