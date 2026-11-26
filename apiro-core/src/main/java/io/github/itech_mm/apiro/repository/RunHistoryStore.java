package io.github.itech_mm.apiro.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.itech_mm.apiro.model.RunRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RunHistoryStore {

    public record Stored(List<RunRecord> runs) {
    }

    private static final String DIRECTORY = ".apiro";
    private static final String SUFFIX = ".runs";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private RunHistoryStore() {
    }

    public static Path directoryFor(Path projectFile, String projectId) {
        Path parent = projectFile.toAbsolutePath().getParent();
        return parent.resolve(DIRECTORY).resolve(projectId + SUFFIX);
    }

    public static Path pathFor(Path projectFile, String projectId, String scenarioId) {
        return directoryFor(projectFile, projectId).resolve(fileName(scenarioId));
    }

    private static String fileName(String scenarioId) {
        return scenarioId.replaceAll("[^A-Za-z0-9._-]", "_") + ".json";
    }

    public static List<RunRecord> load(Path projectFile, String projectId, String scenarioId) {
        Path file = pathFor(projectFile, projectId, scenarioId);
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            Stored stored = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Stored.class);
            if (stored == null || stored.runs() == null) {
                return List.of();
            }
            return stored.runs().stream().filter(Objects::nonNull).toList();
        } catch (IOException | JsonParseException error) {
            return List.of();
        }
    }

    public static void save(Path projectFile, String projectId, String scenarioId, List<RunRecord> runs)
            throws IOException {
        AtomicFiles.write(pathFor(projectFile, projectId, scenarioId), GSON.toJson(new Stored(runs)));
    }

    public static void delete(Path projectFile, String projectId, String scenarioId) throws IOException {
        Files.deleteIfExists(pathFor(projectFile, projectId, scenarioId));
    }

    public static void prune(Path projectFile, String projectId, Set<String> keepScenarioIds) throws IOException {
        Path directory = directoryFor(projectFile, projectId);
        if (!Files.isDirectory(directory)) {
            return;
        }
        Set<String> keep = new HashSet<>();
        for (String id : keepScenarioIds) {
            keep.add(fileName(id));
        }
        List<Path> files;
        try (var entries = Files.list(directory)) {
            files = entries.toList();
        }
        for (Path entry : files) {
            String name = entry.getFileName().toString();
            if (name.endsWith(".json") && !keep.contains(name)) {
                Files.deleteIfExists(entry);
            }
        }
        try (var rest = Files.list(directory)) {
            if (rest.findAny().isEmpty()) {
                Files.delete(directory);
            }
        }
    }

    public static void deleteAll(Path projectFile, String projectId) throws IOException {
        Path directory = directoryFor(projectFile, projectId);
        if (!Files.isDirectory(directory)) {
            return;
        }
        List<Path> files;
        try (var entries = Files.list(directory)) {
            files = entries.toList();
        }
        for (Path entry : files) {
            Files.deleteIfExists(entry);
        }
        Files.deleteIfExists(directory);
    }
}
