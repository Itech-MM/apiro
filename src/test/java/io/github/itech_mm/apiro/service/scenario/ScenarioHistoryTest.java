package io.github.itech_mm.apiro.service.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioHistoryTest {

    private static final java.util.Map<String, ScenarioItem> CACHE = new java.util.HashMap<>();

    private static ScenarioItem with(String... requestIds) {
        return CACHE.computeIfAbsent(String.join(",", requestIds), key -> new ScenarioItem("sc", "Flow", null,
                java.util.Arrays.stream(requestIds).map(id -> Step.of(id, "")).toList(), null, null));
    }

    @Test
    void undoAndRedoWalkBackAndForth() {
        ScenarioHistory history = new ScenarioHistory();
        ScenarioItem one = with("a");
        ScenarioItem two = with("a", "b");
        history.record(one, null, 0);

        assertTrue(history.canUndo());
        assertFalse(history.canRedo());
        assertEquals(one, history.undo(two).orElseThrow());
        assertTrue(history.canRedo());
        assertEquals(two, history.redo(one).orElseThrow());
        assertFalse(history.canRedo());
    }

    @Test
    void newEditClearsRedo() {
        ScenarioHistory history = new ScenarioHistory();
        history.record(with("a"), null, 0);
        history.undo(with("a", "b"));
        history.record(with("a"), null, 10);

        assertFalse(history.canRedo());
    }

    @Test
    void rapidEditsWithTheSameKeyMergeIntoOneStep() {
        ScenarioHistory history = new ScenarioHistory();
        ScenarioItem start = with("a");
        history.record(start, "step:1", 0);
        history.record(with("b"), "step:1", 300);
        history.record(with("c"), "step:1", 700);

        assertEquals(start, history.undo(with("d")).orElseThrow());
        assertFalse(history.canUndo());
    }

    @Test
    void differentKeyOrALongPauseStartsANewStep() {
        ScenarioHistory history = new ScenarioHistory();
        history.record(with("a"), "step:1", 0);
        history.record(with("b"), "step:2", 100);
        history.record(with("c"), "step:2", 5000);

        assertEquals(with("c"), history.undo(with("z")).orElseThrow());
        assertEquals(with("b"), history.undo(with("c")).orElseThrow());
        assertEquals(with("a"), history.undo(with("b")).orElseThrow());
        assertTrue(history.undo(with("a")).isEmpty());
    }

    @Test
    void historyIsCapped() {
        ScenarioHistory history = new ScenarioHistory();
        for (int i = 0; i < 150; i++) {
            history.record(with("r" + i), null, i);
        }
        int steps = 0;
        ScenarioItem cursor = with("end");
        while (history.canUndo()) {
            cursor = history.undo(cursor).orElseThrow();
            steps++;
        }
        assertEquals(100, steps);
        assertEquals(List.of("r50"), cursor.steps().stream().map(Step::requestId).toList());
    }
}
