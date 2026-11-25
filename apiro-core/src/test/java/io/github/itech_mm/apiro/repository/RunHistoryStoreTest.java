package io.github.itech_mm.apiro.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.RunRecord;
import io.github.itech_mm.apiro.model.RunStep;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunHistoryStoreTest {

    private static RunRecord run(long at, String state) {
        RunStep step = new RunStep("s1", "Login", state, 200, 120, "", "Passed · 200 · 120 ms");
        return new RunRecord(String.valueOf(at), at, "Checkout", "Local", state, "", 900, List.of(step),
                "[" + state + "] Login\nScenario done\n");
    }

    @Test
    void savedRunsComeBackEqual(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("shop.apiro.json");
        List<RunRecord> runs = List.of(run(2, "FAILED"), run(1, "PASSED"));

        RunHistoryStore.save(project, "p1", "sc1", runs);

        assertEquals(runs, RunHistoryStore.load(project, "p1", "sc1"));
    }

    @Test
    void missingFileGivesAnEmptyList(@TempDir Path dir) {
        assertTrue(RunHistoryStore.load(dir.resolve("shop.apiro.json"), "p1", "sc1").isEmpty());
    }

    @Test
    void damagedFileGivesAnEmptyList(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("shop.apiro.json");
        Path file = RunHistoryStore.pathFor(project, "p1", "sc1");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ not json");

        assertTrue(RunHistoryStore.load(project, "p1", "sc1").isEmpty());
    }

    @Test
    void scenariosKeepSeparateHistories(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("shop.apiro.json");
        RunHistoryStore.save(project, "p1", "sc1", List.of(run(1, "PASSED")));
        RunHistoryStore.save(project, "p1", "sc2", List.of(run(2, "FAILED")));

        assertEquals("PASSED", RunHistoryStore.load(project, "p1", "sc1").get(0).state());
        assertEquals("FAILED", RunHistoryStore.load(project, "p1", "sc2").get(0).state());
    }

    @Test
    void scenarioIdCannotLeaveTheFolder(@TempDir Path dir) {
        Path project = dir.resolve("shop.apiro.json");
        Path file = RunHistoryStore.pathFor(project, "p1", "../../evil");
        assertEquals(RunHistoryStore.directoryFor(project, "p1"), file.getParent());
    }

    @Test
    void deleteRemovesOneScenario(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("shop.apiro.json");
        RunHistoryStore.save(project, "p1", "sc1", List.of(run(1, "PASSED")));
        RunHistoryStore.save(project, "p1", "sc2", List.of(run(2, "PASSED")));

        RunHistoryStore.delete(project, "p1", "sc1");

        assertTrue(RunHistoryStore.load(project, "p1", "sc1").isEmpty());
        assertFalse(RunHistoryStore.load(project, "p1", "sc2").isEmpty());
    }

    @Test
    void pruneRemovesHistoryOfScenariosThatNoLongerExist(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("shop.apiro.json");
        RunHistoryStore.save(project, "p1", "sc1", List.of(run(1, "PASSED")));
        RunHistoryStore.save(project, "p1", "sc2", List.of(run(2, "PASSED")));
        RunHistoryStore.save(project, "p1", "sc3", List.of(run(3, "PASSED")));

        RunHistoryStore.prune(project, "p1", Set.of("sc2"));

        assertTrue(RunHistoryStore.load(project, "p1", "sc1").isEmpty());
        assertFalse(RunHistoryStore.load(project, "p1", "sc2").isEmpty());
        assertTrue(RunHistoryStore.load(project, "p1", "sc3").isEmpty());
    }

    @Test
    void pruneRemovesTheFolderWhenNothingIsKept(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("shop.apiro.json");
        RunHistoryStore.save(project, "p1", "sc1", List.of(run(1, "PASSED")));

        RunHistoryStore.prune(project, "p1", Set.of());

        assertFalse(Files.exists(RunHistoryStore.directoryFor(project, "p1")));
    }

    @Test
    void pruneIsQuietWhenThereIsNothing(@TempDir Path dir) throws IOException {
        RunHistoryStore.prune(dir.resolve("shop.apiro.json"), "p1", Set.of("sc1"));
    }

    @Test
    void deleteAllRemovesTheFolder(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("shop.apiro.json");
        RunHistoryStore.save(project, "p1", "sc1", List.of(run(1, "PASSED")));
        RunHistoryStore.save(project, "p1", "sc2", List.of(run(2, "PASSED")));

        RunHistoryStore.deleteAll(project, "p1");

        assertFalse(Files.exists(RunHistoryStore.directoryFor(project, "p1")));
    }

    @Test
    void deleteAllIsQuietWhenThereIsNothing(@TempDir Path dir) throws IOException {
        RunHistoryStore.deleteAll(dir.resolve("shop.apiro.json"), "p1");
    }
}
