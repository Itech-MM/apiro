package io.github.itech_mm.apiro.project;

import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.core.utils.DataStorageUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RecentProjects {

    private static final int LIMIT = 10;

    private static final String LAST_KEY = "apiro.lastProject";

    private static final String RECENT_KEY = "apiro.recentProjects";

    private final ObservableList<Path> items = FXCollections.observableArrayList();

    private final ObservableList<Path> view = FXCollections.unmodifiableObservableList(items);

    private String last = "";

    private boolean startupTaken;

    @OnInit
    private void init() {
        try {
            String stored = DataStorageUtil.load(LAST_KEY, String.class);
            last = stored == null ? "" : stored;
            String[] texts = DataStorageUtil.load(RECENT_KEY, String[].class);
            if (texts != null) {
                for (String text : texts) {
                    items.add(Path.of(text));
                }
            }
        } catch (RuntimeException error) {
            items.clear();
            last = "";
        }
    }

    public ObservableList<Path> items() {
        return view;
    }

    public void touch(Path path) {
        if (path == null) {
            return;
        }
        Path normal = path.toAbsolutePath().normalize();
        items.remove(normal);
        items.add(0, normal);
        while (items.size() > LIMIT) {
            items.remove(items.size() - 1);
        }
        last = normal.toString();
        persist();
    }

    public void remove(Path path) {
        if (path == null) {
            return;
        }
        Path normal = path.toAbsolutePath().normalize();
        items.remove(normal);
        if (last.equals(normal.toString())) {
            last = "";
        }
        persist();
    }

    public void clear() {
        items.clear();
        persist();
    }

    public Optional<Path> startupProject() {
        if (startupTaken || last.isEmpty()) {
            return Optional.empty();
        }
        startupTaken = true;
        Path path = Path.of(last);
        if (!Files.isRegularFile(path)) {
            remove(path);
            return Optional.empty();
        }
        return Optional.of(path);
    }

    private void persist() {
        List<String> texts = new ArrayList<>();
        for (Path path : items) {
            texts.add(path.toString());
        }
        try {
            DataStorageUtil.save(LAST_KEY, last.isEmpty() ? null : last);
            DataStorageUtil.save(RECENT_KEY, texts.isEmpty() ? null : texts.toArray(new String[0]));
        } catch (RuntimeException error) {
            return;
        }
    }
}