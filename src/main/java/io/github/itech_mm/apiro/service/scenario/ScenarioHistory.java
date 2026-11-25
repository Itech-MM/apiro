package io.github.itech_mm.apiro.service.scenario;

import io.github.itech_mm.apiro.model.ScenarioItem;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public final class ScenarioHistory {

    private static final int LIMIT = 100;
    private static final long MERGE_WINDOW_MILLIS = 1000;

    private final Deque<ScenarioItem> undo = new ArrayDeque<>();
    private final Deque<ScenarioItem> redo = new ArrayDeque<>();
    private String lastKey;
    private long lastAt;

    public void record(ScenarioItem before, String key, long now) {
        boolean merge = key != null && key.equals(lastKey) && now - lastAt <= MERGE_WINDOW_MILLIS
                && !undo.isEmpty();
        lastKey = key;
        lastAt = now;
        redo.clear();
        if (merge) {
            return;
        }
        undo.addFirst(before);
        while (undo.size() > LIMIT) {
            undo.removeLast();
        }
    }

    public Optional<ScenarioItem> undo(ScenarioItem current) {
        if (undo.isEmpty()) {
            return Optional.empty();
        }
        lastKey = null;
        redo.addFirst(current);
        return Optional.of(undo.removeFirst());
    }

    public Optional<ScenarioItem> redo(ScenarioItem current) {
        if (redo.isEmpty()) {
            return Optional.empty();
        }
        lastKey = null;
        undo.addFirst(current);
        return Optional.of(redo.removeFirst());
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }
}
