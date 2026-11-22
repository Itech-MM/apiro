package io.github.itech_mm.apiro.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LocalStateStore {

    private static final String DIRECTORY = ".apiro";
    private static final String SUFFIX = ".local.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private LocalStateStore() {
    }

    public static Path pathFor(Path projectFile, String projectId) {
        Path parent = projectFile.toAbsolutePath().getParent();
        return parent.resolve(DIRECTORY).resolve(projectId + SUFFIX);
    }

    public static LocalState load(Path projectFile, String projectId) throws IOException {
        Path file = pathFor(projectFile, projectId);
        if (!Files.exists(file)) {
            return LocalState.empty();
        }
        LocalState state = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LocalState.class);
        return state == null ? LocalState.empty() : state;
    }

    public static void save(LocalState state, Path projectFile, String projectId) throws IOException {
        AtomicFiles.write(pathFor(projectFile, projectId), GSON.toJson(state));
    }
}