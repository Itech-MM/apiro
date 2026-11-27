package io.github.itech_mm.apiro.service.manifest;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.github.itech_mm.apiro.spec.Manifest;
import io.github.itech_mm.apiro.spec.ManifestValidator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ManifestReader {

    private static final Gson GSON = new Gson();
    private static final int MAX_PROBLEMS_SHOWN = 5;

    private ManifestReader() {
    }

    public static Manifest read(String json) {
        if (json == null || json.isBlank()) {
            throw new ManifestException("The manifest is empty");
        }
        Manifest manifest;
        try {
            manifest = GSON.fromJson(json, Manifest.class);
        } catch (JsonParseException error) {
            throw new ManifestException("The manifest is not valid JSON: " + error.getMessage(), error);
        }
        if (manifest == null) {
            throw new ManifestException("The manifest is empty");
        }
        List<String> problems;
        try {
            problems = ManifestValidator.validate(manifest);
        } catch (RuntimeException error) {
            throw new ManifestException("The manifest is malformed: " + error.getMessage(), error);
        }
        if (!problems.isEmpty()) {
            throw new ManifestException(describe(problems));
        }
        return manifest;
    }

    public static Manifest read(Path file) throws IOException {
        return read(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static String describe(List<String> problems) {
        StringBuilder text = new StringBuilder("The manifest cannot be imported:");
        int shown = Math.min(problems.size(), MAX_PROBLEMS_SHOWN);
        for (int i = 0; i < shown; i++) {
            text.append("\n- ").append(problems.get(i));
        }
        if (problems.size() > shown) {
            text.append("\n- and ").append(problems.size() - shown).append(" more");
        }
        return text.toString();
    }
}
