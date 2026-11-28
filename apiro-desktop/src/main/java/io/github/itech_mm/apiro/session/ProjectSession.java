package io.github.itech_mm.apiro.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.java_fx.utils.concurrent.BackgroundTaskService;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.repository.LastRun;
import io.github.itech_mm.apiro.repository.OpenProjectsStore;
import io.github.itech_mm.apiro.repository.ProjectStore;
import io.github.itech_mm.apiro.repository.RecentProjects;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

@Service
public class ProjectSession {

    private final ObservableList<ProjectSlot> slots = FXCollections.observableArrayList();
    private final ObservableList<ProjectSlot> slotsView = FXCollections.unmodifiableObservableList(slots);
    private final ObservableList<OpenProject> projects = FXCollections.observableArrayList();
    private final ObservableList<OpenProject> projectsView = FXCollections.unmodifiableObservableList(projects);
    private final ReadOnlyObjectWrapper<OpenProject> active = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<Project> project = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<Path> file = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyIntegerWrapper structure = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyIntegerWrapper saves = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyIntegerWrapper treeVersion = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyStringWrapper saveError = new ReadOnlyStringWrapper("");
    private final StringProperty selectedRequestId = new SimpleStringProperty("");
    private final StringProperty environmentId = new SimpleStringProperty("");
    private final ChangeListener<Number> activeStructure = (obs, old, now) -> structure.set(structure.get() + 1);
    private final ChangeListener<Number> activeSaves = (obs, old, now) -> saves.set(saves.get() + 1);
    private final ChangeListener<Number> projectStructure = (obs, old, now) -> bumpTree();

    private Runnable flush = () -> {
    };

    private Path preferredActive;
    private boolean quietActivation;
    private boolean restored;

    @Use
    private RecentProjects recents;

    @Use
    private OpenProjectsStore store;

    public ObservableList<ProjectSlot> slots() {
        return slotsView;
    }

    public ObservableList<OpenProject> projects() {
        return projectsView;
    }

    public ReadOnlyObjectProperty<OpenProject> activeProperty() {
        return active.getReadOnlyProperty();
    }

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

    public ReadOnlyIntegerProperty treeVersionProperty() {
        return treeVersion.getReadOnlyProperty();
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

    public Optional<ProjectSlot> slotFor(String treeId) {
        return slots.stream().filter(slot -> slot.id().equals(treeId)).findFirst();
    }

    public void restore() {
        if (restored) {
            return;
        }
        restored = true;
        Path saved = store == null ? null : store.activePath();
        preferredActive = saved == null ? null : saved.toAbsolutePath().normalize();
        if (store != null) {
            for (Path path : store.load()) {
                Path normal = path.toAbsolutePath().normalize();
                if (findSlot(normal).isEmpty()) {
                    slots.add(new ProjectSlot(normal));
                }
            }
        }
        bumpTree();
        new ArrayList<>(slots).forEach(slot -> load(slot, false, null));
    }

    public void open(Path path, Consumer<ProjectSlot> done) {
        Path normal = path.toAbsolutePath().normalize();
        Optional<ProjectSlot> existing = findSlot(normal);
        if (existing.isPresent()) {
            ProjectSlot slot = existing.get();
            if (slot.state() == ProjectSlot.State.READY) {
                activate(slot.open());
                if (done != null) {
                    done.accept(slot);
                }
                return;
            }
            if (slot.state() == ProjectSlot.State.LOADING) {
                return;
            }
            slot.loading();
            bumpTree();
            load(slot, true, done);
            return;
        }
        ProjectSlot slot = new ProjectSlot(normal);
        slots.add(slot);
        persist();
        bumpTree();
        load(slot, true, done);
    }

    public void create(Path path) throws IOException {
        create(path, null);
    }

    public void create(Path path, Project initial) throws IOException {
        Path normal = path.toAbsolutePath().normalize();
        if (findSlot(normal).isPresent()) {
            throw new IOException("That file is already open as a project");
        }
        OpenProject created = initial == null ? OpenProject.create(normal) : OpenProject.create(normal, initial);
        ProjectSlot slot = new ProjectSlot(normal);
        slot.ready(created);
        slots.add(slot);
        projects.add(created);
        created.structureProperty().addListener(projectStructure);
        activate(created);
        bumpTree();
        if (recents != null) {
            recents.touch(normal);
        }
        persist();
    }

    public void save() throws IOException {
        OpenProject current = active.get();
        if (current == null) {
            return;
        }
        flush.run();
        current.save();
    }

    public void selectEnvironment(String id) {
        OpenProject current = active.get();
        if (current != null) {
            current.selectEnvironment(id);
        }
    }

    public Optional<RequestItem> selectedRequest() {
        OpenProject current = active.get();
        return current == null ? Optional.empty() : current.selectedRequest();
    }

    public void updateRequest(RequestItem request) {
        OpenProject current = active.get();
        if (current != null) {
            current.updateRequest(request);
        }
    }

    public void addItem(String parentId, ProjectItem item) {
        OpenProject current = active.get();
        if (current != null) {
            current.addItem(parentId, item);
        }
    }

    public void rename(String id, String name) {
        OpenProject current = active.get();
        if (current != null) {
            current.rename(id, name);
        }
    }

    public void delete(String id) {
        OpenProject current = active.get();
        if (current != null) {
            current.delete(id);
        }
    }

    public Optional<String> duplicate(String id) {
        flush.run();
        OpenProject current = active.get();
        return current == null ? Optional.empty() : current.duplicate(id);
    }

    public void move(String id, String parentId, String anchorId, boolean after) {
        OpenProject current = active.get();
        if (current != null) {
            current.move(id, parentId, anchorId, after);
        }
    }

    public void updateProject(UnaryOperator<Project> change) {
        OpenProject current = active.get();
        if (current != null) {
            current.updateProject(change);
        }
    }

    public void activateProject(String projectId) {
        findById(projectId).ifPresent(this::activate);
    }

    public void closeProject(String projectId) {
        Optional<OpenProject> found = findById(projectId);
        if (found.isEmpty()) {
            return;
        }
        OpenProject target = found.get();
        int index = projects.indexOf(target);
        target.structureProperty().removeListener(projectStructure);
        projects.remove(target);
        slots.removeIf(slot -> slot.open() == target);
        if (active.get() == target) {
            activate(projects.isEmpty() ? null : projects.get(Math.min(index, projects.size() - 1)));
        }
        bumpTree();
        persist();
    }

    /** Deletes the project file and its local state from disk, then closes the project. */
    public void deleteFromDisk(String projectId) throws IOException {
        Optional<OpenProject> found = findById(projectId);
        if (found.isEmpty()) {
            return;
        }
        OpenProject target = found.get();
        Path file = target.file();
        ProjectStore.delete(file, target.id());
        closeProject(projectId);
        if (recents != null) {
            recents.remove(file);
        }
    }

    public void removePlaceholder(String slotId) {
        slotFor(slotId).filter(slot -> slot.state() != ProjectSlot.State.READY).ifPresent(slot -> {
            slots.remove(slot);
            activateFallback();
            bumpTree();
            persist();
        });
    }

    public void selectRequest(String projectId, String requestId) {
        findById(projectId).ifPresent(target -> {
            target.selectedRequestIdProperty().set(requestId == null ? "" : requestId);
            activate(target);
        });
    }

    public Optional<LastRun> lastRun(String requestId) {
        OpenProject current = active.get();
        return current == null ? Optional.empty() : current.lastRun(requestId);
    }
    
    private void load(ProjectSlot slot, boolean userInitiated, Consumer<ProjectSlot> done) {
        Path target = slot.file();
        BackgroundTaskService.getInstance().executeTask(
                () -> ProjectStore.open(target),
                opened -> finishLoad(slot, opened, userInitiated, done),
                error -> failLoad(slot, describe(target, error), done));
    }

    private void finishLoad(ProjectSlot slot, ProjectStore.Opened opened, boolean userInitiated,
            Consumer<ProjectSlot> done) {
        if (!slots.contains(slot)) {
            return;
        }
        OpenProject loaded = OpenProject.from(slot.file(), opened);
        Optional<ProjectSlot> clash = slots.stream()
                .filter(other -> other != slot && other.state() == ProjectSlot.State.READY
                        && other.id().equals(loaded.id()))
                .findFirst();
        if (clash.isPresent()) {
            failLoad(slot, "A project with the same id is already open: " + clash.get().file(), done);
            return;
        }
        slot.ready(loaded);
        addInSlotOrder(loaded);
        loaded.structureProperty().addListener(projectStructure);
        if (userInitiated) {
            activate(loaded);
            if (recents != null) {
                recents.touch(slot.file());
            }
        } else {
            activateAfterRestore(slot);
        }
        bumpTree();
        persist();
        if (done != null) {
            done.accept(slot);
        }
    }

    /**
     * Loads finish in any order, but the tree must show projects in the saved slot order. The slot of {@code loaded}
     * is already marked ready, so its position is the number of earlier slots whose project is already loaded.
     */
    private void addInSlotOrder(OpenProject loaded) {
        int index = 0;
        for (ProjectSlot slot : slots) {
            if (slot.open() == loaded) {
                break;
            }
            if (slot.open() != null && projects.contains(slot.open())) {
                index++;
            }
        }
        projects.add(Math.min(index, projects.size()), loaded);
    }

    private void failLoad(ProjectSlot slot, String reason, Consumer<ProjectSlot> done) {
        if (!slots.contains(slot)) {
            return;
        }
        slot.failed(reason);
        activateFallback();
        bumpTree();
        if (done != null) {
            done.accept(slot);
        }
    }

    private void activateAfterRestore(ProjectSlot slot) {
        boolean preferred = slot.file().equals(preferredActive);
        boolean fallback = active.get() == null && !preferredPending();
        if (preferred || fallback) {
            quiet(() -> activate(slot.open()));
        }
    }

    private void activateFallback() {
        if (active.get() != null || preferredPending()) {
            return;
        }
        slots.stream()
                .filter(slot -> slot.state() == ProjectSlot.State.READY)
                .findFirst()
                .ifPresent(slot -> quiet(() -> activate(slot.open())));
    }

    private boolean preferredPending() {
        return preferredActive != null && slots.stream()
                .anyMatch(slot -> slot.file().equals(preferredActive) && slot.state() == ProjectSlot.State.LOADING);
    }

    private void quiet(Runnable action) {
        quietActivation = true;
        try {
            action.run();
        } finally {
            quietActivation = false;
        }
    }

    private void activate(OpenProject next) {
        OpenProject previous = active.get();
        if (previous == next) {
            return;
        }
        if (previous != null) {
            selectedRequestId.unbindBidirectional(previous.selectedRequestIdProperty());
            environmentId.unbindBidirectional(previous.environmentIdProperty());
            project.unbind();
            saveError.unbind();
            previous.structureProperty().removeListener(activeStructure);
            previous.savesProperty().removeListener(activeSaves);
        }
        if (next == null) {
            selectedRequestId.set("");
            file.set(null);
            project.set(null);
            environmentId.set("");
            saveError.set("");
            active.set(null);
            structure.set(structure.get() + 1);
            return;
        }
        selectedRequestId.bindBidirectional(next.selectedRequestIdProperty());
        file.set(next.file());
        project.bind(next.projectProperty());
        saveError.bind(next.saveErrorProperty());
        environmentId.bindBidirectional(next.environmentIdProperty());
        next.structureProperty().addListener(activeStructure);
        next.savesProperty().addListener(activeSaves);
        active.set(next);
        structure.set(structure.get() + 1);
        if (!quietActivation) {
            preferredActive = next.file();
            persist();
        }
    }

    private void persist() {
        if (store == null) {
            return;
        }
        List<Path> paths = new ArrayList<>();
        for (ProjectSlot slot : slots) {
            paths.add(slot.file());
        }
        store.save(paths, preferredActive);
    }

    private static String describe(Path target, Exception error) {
        if (Files.notExists(target)) {
            return "File not found: " + target + ". It may have been moved, renamed or deleted.";
        }
        String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return "Could not read " + target.getFileName() + ": " + detail;
    }

    private Optional<OpenProject> findById(String projectId) {
        return projects.stream().filter(p -> p.id().equals(projectId)).findFirst();
    }

    private Optional<ProjectSlot> findSlot(Path target) {
        return slots.stream().filter(slot -> slot.file().equals(target)).findFirst();
    }

    private void bumpTree() {
        treeVersion.set(treeVersion.get() + 1);
    }
}