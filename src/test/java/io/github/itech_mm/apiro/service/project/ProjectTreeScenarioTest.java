package io.github.itech_mm.apiro.service.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import io.github.itech_mm.apiro.model.Variable;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectTreeScenarioTest {

    private static final RequestItem LOGIN = new RequestItem("r1", "Login", HttpMethod.POST, "/login", null, null,
            null, null, null);

    private static ScenarioItem scenario() {
        return new ScenarioItem("sc1", "Checkout", List.of(Variable.of("email", "a@b.c")),
                List.of(new Step("s1", "r1", "Login", null, null, 0, null, 0, 0),
                        new Step("s2", "r1", "Again", null, null, 0, null, 0, 0)),
                List.of(new Edge("s1", "s2")), OnFailure.CONTINUE);
    }

    private static Project project() {
        Folder flows = new Folder("f1", "Flows", null, null, null, null, List.of(scenario()));
        return new Project(null, 0, "p1", "Shop", null, null, null, null, null, null, List.of(LOGIN, flows));
    }

    @Test
    void renameKeepsScenarioContent() {
        Project renamed = ProjectTree.withRenamed(project(), "sc1", "Purchase flow");
        ScenarioItem found = assertInstanceOf(ScenarioItem.class, renamed.findItem("sc1").orElseThrow());
        assertEquals("Purchase flow", found.name());
        assertEquals(scenario().steps(), found.steps());
        assertEquals(scenario().edges(), found.edges());
        assertEquals(scenario().variables(), found.variables());
        assertEquals(OnFailure.CONTINUE, found.onFailure());
    }

    @Test
    void copyGetsNewIdAndSuffixButKeepsSteps() {
        ProjectItem copy = ProjectTree.copyOf(scenario());
        ScenarioItem duplicate = assertInstanceOf(ScenarioItem.class, copy);
        assertNotEquals("sc1", duplicate.id());
        assertEquals("Checkout copy", duplicate.name());
        assertEquals(scenario().steps(), duplicate.steps());
        assertEquals(scenario().edges(), duplicate.edges());
        assertEquals(OnFailure.CONTINUE, duplicate.onFailure());
    }

    @Test
    void withScenarioReplacesInPlaceInsideFolder() {
        ScenarioItem edited = new ScenarioItem("sc1", "Checkout", null, List.of(Step.of("r1", "Only")), null, null);
        Project updated = ProjectTree.withScenario(project(), edited);
        assertEquals(edited, updated.findItem("sc1").orElseThrow());
        Folder folder = (Folder) updated.findItem("f1").orElseThrow();
        assertEquals(1, folder.items().size());
        assertEquals(2, updated.items().size());
    }

    @Test
    void scenarioCanBeAddedToFolderAndTopLevel() {
        ScenarioItem extra = ScenarioItem.of("Smoke");
        Project inFolder = ProjectTree.withAdded(project(), "f1", extra);
        assertEquals("f1", ProjectTree.parentOf(inFolder, extra.id()).orElseThrow());
        Project topLevel = ProjectTree.withAdded(project(), "", extra);
        assertEquals("", ProjectTree.parentOf(topLevel, extra.id()).orElseThrow());
    }

    @Test
    void scenarioCanBeMovedAndRemoved() {
        Project moved = ProjectTree.withMoved(project(), "sc1", "", "r1", true);
        assertEquals("", ProjectTree.parentOf(moved, "sc1").orElseThrow());
        assertEquals("sc1", moved.items().get(1).id());
        Project removed = ProjectTree.without(project(), "sc1");
        assertTrue(removed.findItem("sc1").isEmpty());
        assertTrue(removed.findItem("f1").isPresent());
    }

    @Test
    void requestCountIgnoresScenarios() {
        Folder flows = (Folder) project().findItem("f1").orElseThrow();
        assertEquals(0, ProjectTree.requestCount(scenario()));
        assertEquals(0, ProjectTree.requestCount(flows));
        Folder mixed = Folder.of("Mixed", List.of(LOGIN, scenario()));
        assertEquals(1, ProjectTree.requestCount(mixed));
        assertFalse(ProjectTree.requestCount(mixed) > 1);
    }

    @Test
    void findStepLooksUpById() {
        assertEquals("Again", scenario().findStep("s2").orElseThrow().name());
        assertTrue(scenario().findStep("missing").isEmpty());
    }
}
