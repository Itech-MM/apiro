package io.github.itech_mm.apiro.project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.UnaryOperator;

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

public final class OpenProject {

    private static final String EXTENSION = ".apiro.json";

    private final Path file;
    private final ReadOnlyObjectWrapper<Project> project = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyIntegerWrapper structure = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyIntegerWrapper saves = new ReadOnlyIntegerWrapper(0);
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

    public static OpenProject create(Path path) throws IOException {
        Project created = Project.create(nameFrom(path));
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

    public StringProperty selectedRequestIdProperty() {
        return selectedRequestId;
    }

    public StringProperty environmentIdProperty() {
        return environmentId;
    }

    public void save() throws IOException {
        state = ProjectStore.save(project.get(), state, file);
        saves.set(saves.get() + 1);
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

    private void changed(Project next) {
        project.set(next);
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