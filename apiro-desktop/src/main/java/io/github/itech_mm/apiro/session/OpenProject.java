package io.github.itech_mm.apiro.session;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import io.github.itech_mm.apiro.model.Environment;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.RunRecord;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.repository.LastRun;
import io.github.itech_mm.apiro.repository.LocalState;
import io.github.itech_mm.apiro.repository.LocalStateStore;
import io.github.itech_mm.apiro.repository.ProjectStore;
import io.github.itech_mm.apiro.repository.RunHistoryStore;
import io.github.itech_mm.apiro.service.project.ProjectTree;
import io.github.itech_mm.apiro.service.scenario.RunRecords;
import io.github.itech_mm.apiro.service.scenario.RunStore;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class OpenProject {

    private static final String EXTENSION = ".apiro.json";

    private final Path file;
    private final ReadOnlyObjectWrapper<Project> project = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyIntegerWrapper structure = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyIntegerWrapper saves = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyStringWrapper saveError = new ReadOnlyStringWrapper("");
    private final StringProperty selectedRequestId = new SimpleStringProperty("");
    private final StringProperty environmentId = new SimpleStringProperty("");
    private LocalState state;

    private OpenProject(Path file, Project loaded, LocalState state) {
        this.file = file;
        this.state = state;
        project.set(loaded);
        environmentId.set(initialEnvironment(loaded));
    }

    public static OpenProject open(Path path) throws IOException {
        ProjectStore.Opened opened = ProjectStore.open(path);
        return new OpenProject(path, opened.project(), opened.state());
    }
    
    public static OpenProject from(Path path, ProjectStore.Opened opened) {
        return new OpenProject(path, opened.project(), opened.state());
    }

    public static OpenProject create(Path path) throws IOException {
        return create(path, Project.create(nameFrom(path)));
    }

    public static OpenProject create(Path path, Project created) throws IOException {
        LocalState saved = ProjectStore.save(created, LocalState.empty(), path);
        return new OpenProject(path, created, saved);
    }

    public Path file() {
        return file;
    }

    public Project project() {
        return project.get();
    }

    public String id() {
        return project.get().id();
    }

    public ReadOnlyObjectProperty<Project> projectProperty() {
        return project.getReadOnlyProperty();
    }

    public ReadOnlyIntegerProperty structureProperty() {
        return structure.getReadOnlyProperty();
    }

    public ReadOnlyIntegerProperty savesProperty() {
        return saves.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty saveErrorProperty() {
        return saveError.getReadOnlyProperty();
    }

    public StringProperty selectedRequestIdProperty() {
        return selectedRequestId;
    }

    public StringProperty environmentIdProperty() {
        return environmentId;
    }

    public void save() throws IOException {
        try {
            state = ProjectStore.save(project.get(), state, file);
        } catch (IOException | RuntimeException error) {
            saveError.set(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            throw error;
        }
        saveError.set("");
        saves.set(saves.get() + 1);
        pruneRuns();
    }

    private void autoSave() {
        try {
            save();
        } catch (IOException error) {
            return;
        }
    }

    private void pruneRuns() {
        Project saved = project.get();
        try {
            RunHistoryStore.prune(file, saved.id(), RunRecords.scenarioIds(saved));
        } catch (IOException error) {
            return;
        }
    }

    public void selectEnvironment(String id) {
        String next = id == null ? "" : id;
        state = state.withActiveEnvironment(next);
        environmentId.set(next);
    }

    public Optional<RequestItem> selectedRequest() {
        return project.get().findItem(selectedRequestId.get())
                .filter(RequestItem.class::isInstance)
                .map(RequestItem.class::cast);
    }

    public void updateRequest(RequestItem request) {
        project.set(ProjectTree.withRequest(project.get(), request));
    }

    public void addItem(String parentId, ProjectItem item) {
        changed(ProjectTree.withAdded(project.get(), parentId, item));
    }

    public void rename(String id, String name) {
        if (name != null && !name.isBlank()) {
            changed(ProjectTree.withRenamed(project.get(), id, name.trim()));
        }
    }

    public void delete(String id) {
        changed(ProjectTree.without(project.get(), id));
    }

    public Optional<String> duplicate(String id) {
        Project current = project.get();
        Optional<ProjectItem> original = current.findItem(id);
        if (original.isEmpty()) {
            return Optional.empty();
        }
        ProjectItem copy = ProjectTree.copyOf(original.get());
        changed(ProjectTree.withInsertedAfter(current, id, copy));
        return Optional.of(copy.id());
    }

    public void move(String id, String parentId, String anchorId, boolean after) {
        Project current = project.get();
        Project moved = ProjectTree.withMoved(current, id, parentId, anchorId, after);
        if (moved != current) {
            changed(moved);
        }
    }

    public void updateProject(UnaryOperator<Project> change) {
        Project current = project.get();
        Project next = change.apply(current);
        if (next == null || next.equals(current)) {
            return;
        }
        if (next.findEnvironment(environmentId.get()).isEmpty()) {
            selectEnvironment(next.environments().stream().map(Environment::id).findFirst().orElse(""));
        }
        changed(next);
    }

    public Optional<LastRun> lastRun(String requestId) {
        return Optional.ofNullable(state.lastRun(requestId));
    }

    public void recordRun(String requestId, LastRun run) {
        if (requestId == null || requestId.isBlank() || run == null) {
            return;
        }
        state = state.withLastRun(requestId, run);
        try {
            LocalStateStore.save(state, file, project.get().id());
        } catch (IOException error) {
            return;
        }
    }
    
    private void changed(Project next) {
        project.set(next);
        structure.set(structure.get() + 1);
        autoSave();
    }

    private String initialEnvironment(Project loaded) {
        String active = state.activeEnvironmentId();
        if (loaded.findEnvironment(active).isPresent()) {
            return active;
        }
        return loaded.environments().stream().map(Environment::id).findFirst().orElse("");
    }

    private static String nameFrom(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(EXTENSION) ? name.substring(0, name.length() - EXTENSION.length()) : name;
    }
    
    public Map<String, RequestItem> drafts() {
        return state.drafts();
    }

    public void setDrafts(Map<String, RequestItem> next) {
        if (state.drafts().equals(next)) {
            return;
        }
        state = state.withDrafts(next);
        try {
            LocalStateStore.save(state, file, project.get().id());
        } catch (IOException error) {
            return;
        }
    }

    public RunStore runStore() {
        Path projectFile = file;
        String projectId = project.get().id();
        return new RunStore() {
            @Override
            public List<RunRecord> load(String scenarioId) {
                return RunHistoryStore.load(projectFile, projectId, scenarioId);
            }

            @Override
            public List<RunRecord> add(String scenarioId, RunRecord record) {
                List<RunRecord> next = RunRecords.add(load(scenarioId), record, RunRecords.LIMIT);
                try {
                    RunHistoryStore.save(projectFile, projectId, scenarioId, next);
                } catch (IOException error) {
                    return next;
                }
                return next;
            }

            @Override
            public void clear(String scenarioId) {
                try {
                    RunHistoryStore.delete(projectFile, projectId, scenarioId);
                } catch (IOException error) {
                    return;
                }
            }
        };
    }

    public Map<String, ScenarioItem> scenarioDrafts() {
        return state.scenarioDrafts();
    }

    public void setScenarioDrafts(Map<String, ScenarioItem> next) {
        if (state.scenarioDrafts().equals(next)) {
            return;
        }
        state = state.withScenarioDrafts(next);
        try {
            LocalStateStore.save(state, file, project.get().id());
        } catch (IOException error) {
            return;
        }
    }
}
