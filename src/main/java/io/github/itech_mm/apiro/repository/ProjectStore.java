package io.github.itech_mm.apiro.repository;

import io.github.itech_mm.apiro.model.Project;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ProjectStore {

    public record Opened(Project project, LocalState state) {
    }

    private ProjectStore() {
    }

    public static Opened open(Path file) throws IOException {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        Project stored = ProjectJson.fromJson(json);
        LocalState state = LocalStateStore.load(file, stored.id());
        return new Opened(SecretSplitter.merge(stored, state), state);
    }

    public static LocalState save(Project project, LocalState state, Path file) throws IOException {
        SecretSplitter.Split split = SecretSplitter.split(project, state);
        LocalStateStore.save(split.state(), file, project.id());
        AtomicFiles.write(file, ProjectJson.toJson(split.project()));
        return split.state();
    }

    /**
     * Deletes the project file, then makes a best effort to remove its local state and the then-empty state folder.
     * A failure on the project file is reported; leftover local state is not.
     */
    public static void delete(Path file, String projectId) throws IOException {
        Files.delete(file);
        Path state = LocalStateStore.pathFor(file, projectId);
        try {
            Files.deleteIfExists(state);
            Path directory = state.getParent();
            if (directory != null && Files.isDirectory(directory)) {
                try (var entries = Files.list(directory)) {
                    if (entries.findAny().isEmpty()) {
                        Files.delete(directory);
                    }
                }
            }
        } catch (IOException ignored) {
            // The project file is gone; stale local state is harmless.
        }
    }
}
