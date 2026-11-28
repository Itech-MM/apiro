package io.github.itech_mm.apiro.cli;

import com.google.gson.JsonParseException;
import io.github.itech_mm.apiro.repository.ProjectStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

final class ProjectFiles {

    private ProjectFiles() {
    }

    static ProjectStore.Opened open(String file) {
        Path path;
        try {
            path = Path.of(file);
        } catch (InvalidPathException error) {
            throw new CliException("Invalid project path: " + file);
        }
        if (!Files.isRegularFile(path)) {
            throw new CliException("Project file not found: " + file);
        }
        try {
            return ProjectStore.open(path);
        } catch (IOException | JsonParseException error) {
            throw new CliException("Cannot read " + file + ": " + error.getMessage());
        }
    }
}
