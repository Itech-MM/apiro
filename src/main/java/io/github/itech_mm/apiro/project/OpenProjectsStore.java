package io.github.itech_mm.apiro.project;

import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.utils.DataStorageUtil;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class OpenProjectsStore {

    private static final String LIST_KEY = "apiro.openProjects";

    private static final String ACTIVE_KEY = "apiro.activeProject";

    public List<Path> load() {
        List<Path> paths = new ArrayList<>();
        try {
            String[] texts = DataStorageUtil.load(LIST_KEY, String[].class);
            if (texts == null) {
                return paths;
            }
            for (String text : texts) {
                if (text == null || text.isBlank()) {
                    continue;
                }
                try {
                    paths.add(Path.of(text));
                } catch (InvalidPathException error) {
                    continue;
                }
            }
        } catch (RuntimeException error) {
            paths.clear();
        }
        return paths;
    }

    public Path activePath() {
        try {
            String text = DataStorageUtil.load(ACTIVE_KEY, String.class);
            return text == null || text.isBlank() ? null : Path.of(text);
        } catch (RuntimeException error) {
            return null;
        }
    }

    public void save(List<Path> paths, Path active) {
        List<String> texts = new ArrayList<>();
        for (Path path : paths) {
            texts.add(path.toString());
        }
        try {
            DataStorageUtil.save(LIST_KEY, texts.isEmpty() ? null : texts.toArray(new String[0]));
            DataStorageUtil.save(ACTIVE_KEY, active == null ? null : active.toString());
        } catch (RuntimeException error) {
            return;
        }
    }
}