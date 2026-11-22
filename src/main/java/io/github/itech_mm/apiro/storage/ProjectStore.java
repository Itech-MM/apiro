package io.github.itech_mm.apiro.storage;

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
}