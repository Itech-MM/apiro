package io.github.itech_mm.apiro.project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.UnaryOperator;

import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_mm.apiro.model.Environment;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.storage.LocalState;
import io.github.itech_mm.apiro.storage.ProjectStore;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

@Service
public class ProjectSession {

    private static final String EXTENSION = ".apiro.json";

    private final ReadOnlyObjectWrapper<Project> project = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<Path> file = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyIntegerWrapper structure = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyIntegerWrapper saves = new ReadOnlyIntegerWrapper(0);
    private final StringProperty selectedRequestId = new SimpleStringProperty("");
    private final StringProperty environmentId = new SimpleStringProperty("");
    private LocalState state = LocalState.empty();
    private Runnable flush = () -> {
    };

    public ReadOnlyObjectProperty<Project> projectProperty() {
        return project.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<Path> fileProperty() {
        return file.getReadOnlyProperty();
    }

    public ReadOnlyIntegerProperty structureProperty() {
        return structure.getReadOnlyProperty();
    }

    public ReadOnlyIntegerProperty savesProperty() {
        return saves.getReadOnlyProperty();
    }
    
    public StringProperty selectedRequestIdProperty() {
        return selectedRequestId;
    }

    public StringProperty environmentIdProperty() {
        return environmentId;
    }

    public boolean isOpen() {
        return project.get() != null;
    }

    public Project project() {
        return project.get();
    }

    public void registerFlush(Runnable next) {
        flush = next == null ? () -> {
        } : next;
    }

    public void open(Path path) throws IOException {
        ProjectStore.Opened opened = ProjectStore.open(path);
        state = opened.state();
        load(opened.project(), path);
    }

    public void create(Path path) throws IOException {
        Project created = Project.create(nameFrom(path));
        state = ProjectStore.save(created, LocalState.empty(), path);
        load(created, path);
    }

    public void save() throws IOException {
        if (!isOpen()) {
            return;
        }
        flush.run();
        state = ProjectStore.save(project.get(), state, file.get());
        saves.set(saves.get() + 1);
    }

    public void selectEnvironment(String id) {
        String next = id == null ? "" : id;
        state = state.withActiveEnvironment(next);
        environmentId.set(next);
    }

    public Optional<RequestItem> selectedRequest() {
        Project current = project.get();
        if (current == null) {
            return Optional.empty();
        }
        return current.findItem(selectedRequestId.get())
                .filter(RequestItem.class::isInstance)
                .map(RequestItem.class::cast);
    }

    public void updateRequest(RequestItem request) {
        Project current = project.get();
        if (current != null) {
            project.set(ProjectTree.withRequest(current, request));
        }
    }

    public void addItem(String parentId, ProjectItem item) {
        Project current = project.get();
        if (current != null) {
            changed(ProjectTree.withAdded(current, parentId, item));
        }
    }

    public void rename(String id, String name) {
        Project current = project.get();
        if (current != null && name != null && !name.isBlank()) {
            changed(ProjectTree.withRenamed(current, id, name.trim()));
        }
    }

    public void delete(String id) {
        Project current = project.get();
        if (current != null) {
            changed(ProjectTree.without(current, id));
        }
    }

    public Optional<String> duplicate(String id) {
        flush.run();
        Project current = project.get();
        if (current == null) {
            return Optional.empty();
        }
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
        if (current == null) {
            return;
        }
        Project moved = ProjectTree.withMoved(current, id, parentId, anchorId, after);
        if (moved != current) {
            changed(moved);
        }
    }

    public void updateProject(UnaryOperator<Project> change) {
        Project current = project.get();
        if (current == null) {
            return;
        }
        Project next = change.apply(current);
        if (next == null || next.equals(current)) {
            return;
        }
        if (next.findEnvironment(environmentId.get()).isEmpty()) {
            selectEnvironment(next.environments().stream().map(Environment::id).findFirst().orElse(""));
        }
        changed(next);
    }
    
    private void changed(Project next) {
        project.set(next);
        structure.set(structure.get() + 1);
    }

    private void load(Project loaded, Path path) {
        selectedRequestId.set("");
        file.set(path);
        project.set(loaded);
        environmentId.set(initialEnvironment(loaded));
        structure.set(structure.get() + 1);
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
}