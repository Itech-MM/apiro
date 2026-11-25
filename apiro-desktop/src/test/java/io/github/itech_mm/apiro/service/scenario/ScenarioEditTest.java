package io.github.itech_mm.apiro.service.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.Edge;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.OnFailure;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioEditTest {

    private static ScenarioItem three() {
        return new ScenarioItem("sc", "Flow", null,
                List.of(new Step("a", "r1", "A", null, null, 0, null, 0, 0),
                        new Step("b", "r2", "B", null, null, 0, null, 0, 0),
                        new Step("c", "r3", "C", null, null, 0, null, 0, 0)),
                null, null);
    }

    private static List<String> ids(ScenarioItem scenario) {
        return scenario.steps().stream().map(Step::id).toList();
    }

    @Test
    void appendAddsStepAtTheEnd() {
        ScenarioItem next = ScenarioEdit.withAppended(three(), "r4", "D");

        assertEquals(4, next.steps().size());
        assertEquals("r4", next.steps().get(3).requestId());
        assertTrue(next.edges().isEmpty());
    }

    @Test
    void insertPlacesTheStepAtTheGapAndClamps() {
        assertEquals("r9", ScenarioEdit.withInserted(three(), "r9", "", 1).steps().get(1).requestId());
        assertEquals("r9", ScenarioEdit.withInserted(three(), "r9", "", -5).steps().get(0).requestId());
        assertEquals("r9", ScenarioEdit.withInserted(three(), "r9", "", 99).steps().get(3).requestId());
        assertEquals(4, ScenarioEdit.withInserted(three(), "r9", "", 2).steps().size());
    }

    @Test
    void restoringContentKeepsTheCurrentNameAndId() {
        ScenarioItem renamed = new ScenarioItem("sc", "Renamed", null, three().steps(), null, null);
        ScenarioItem old = new ScenarioItem("sc", "Flow", null, three().steps().subList(0, 1), null,
                OnFailure.CONTINUE);

        ScenarioItem restored = ScenarioEdit.withContent(renamed, old);

        assertEquals("Renamed", restored.name());
        assertEquals(1, restored.steps().size());
        assertEquals(OnFailure.CONTINUE, restored.onFailure());
    }

    @Test
    void sameContentIgnoresTheName() {
        ScenarioItem base = three();
        ScenarioItem renamed = new ScenarioItem("sc", "Other", base.variables(), base.steps(), base.edges(),
                base.onFailure());
        ScenarioItem edited = ScenarioEdit.withOnFailure(base, OnFailure.CONTINUE);

        assertTrue(ScenarioEdit.sameContent(base, renamed));
        assertFalse(ScenarioEdit.sameContent(base, edited));
        assertFalse(ScenarioEdit.sameContent(base, ScenarioEdit.withoutStep(base, "a")));
    }

    @Test
    void moveReordersAndClampsTheTarget() {
        assertEquals(List.of("b", "c", "a"), ids(ScenarioEdit.withMovedStep(three(), "a", 99)));
        assertEquals(List.of("c", "a", "b"), ids(ScenarioEdit.withMovedStep(three(), "c", -4)));
        ScenarioItem same = three();
        assertSame(same, ScenarioEdit.withMovedStep(same, "b", 1));
        assertSame(same, ScenarioEdit.withMovedStep(same, "missing", 0));
    }

    @Test
    void removeDropsOnlyThatStep() {
        assertEquals(List.of("a", "c"), ids(ScenarioEdit.withoutStep(three(), "b")));
        ScenarioItem same = three();
        assertSame(same, ScenarioEdit.withoutStep(same, "zzz"));
    }

    @Test
    void existingEdgesAreRebuiltAsAChainInTheNewOrder() {
        ScenarioItem base = three();
        ScenarioItem linked = new ScenarioItem("sc", "Flow", null, base.steps(),
                List.of(new Edge("a", "b"), new Edge("b", "c")), null);

        ScenarioItem moved = ScenarioEdit.withMovedStep(linked, "c", 0);

        assertEquals(List.of(new Edge("c", "a"), new Edge("a", "b")), moved.edges());
        assertTrue(ScenarioChain.order(moved).valid());
    }

    @Test
    void stepUpdateKeepsPositionAndIgnoresUnknownIds() {
        ScenarioItem base = three();
        Step renamed = new Step("b", "r2", "Renamed", null, null, 250, OnFailure.CONTINUE, 0, 0);

        ScenarioItem next = ScenarioEdit.withStep(base, renamed);

        assertEquals(List.of("a", "b", "c"), ids(next));
        assertEquals("Renamed", next.steps().get(1).name());
        assertSame(base, ScenarioEdit.withStep(base, new Step("x", "", "", null, null, 0, null, 0, 0)));
    }

    @Test
    void policyChangeIsAppliedOnce() {
        ScenarioItem base = three();
        ScenarioItem next = ScenarioEdit.withOnFailure(base, OnFailure.CONTINUE);

        assertEquals(OnFailure.CONTINUE, next.onFailure());
        assertSame(next, ScenarioEdit.withOnFailure(next, OnFailure.CONTINUE));
    }

    @Test
    void requestsAreListedWithTheirFolderPath() {
        RequestItem login = new RequestItem("r1", "Login", HttpMethod.POST, "http://x/login", null, null, null, null,
                null);
        RequestItem orders = new RequestItem("r2", "List", HttpMethod.GET, "http://x/o", null, null, null, null,
                null);
        Folder shop = Folder.of("Shop", List.<ProjectItem>of(orders));
        Project project = new Project(null, 0, "p", "P", null, null, null, null, null, null, List.of(login, shop));

        List<ScenarioEdit.RequestRef> refs = ScenarioEdit.requests(project);

        assertEquals(List.of("Login", "Shop / List"), refs.stream().map(ScenarioEdit.RequestRef::label).toList());
        assertEquals(HttpMethod.POST, refs.get(0).method());
    }

    @Test
    void sensitiveVariablesAreMaskedInLogs() {
        assertEquals("••••", ScenarioEdit.maskedValue("accessToken", "abc"));
        assertEquals("42", ScenarioEdit.maskedValue("orderId", "42"));
        assertEquals(81, ScenarioEdit.maskedValue("note", "x".repeat(200)).length());
    }
}
