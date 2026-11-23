package io.github.itech_mm.apiro.project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.UnaryOperator;

import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

@Service
public class ProjectSession {

	private final ObservableList<OpenProject> projects = FXCollections.observableArrayList();
	private final ObservableList<OpenProject> projectsView = FXCollections.unmodifiableObservableList(projects);
	private final ReadOnlyObjectWrapper<OpenProject> active = new ReadOnlyObjectWrapper<>();
	private final ReadOnlyObjectWrapper<Project> project = new ReadOnlyObjectWrapper<>();
	private final ReadOnlyObjectWrapper<Path> file = new ReadOnlyObjectWrapper<>();
	private final ReadOnlyIntegerWrapper structure = new ReadOnlyIntegerWrapper(0);
	private final ReadOnlyIntegerWrapper saves = new ReadOnlyIntegerWrapper(0);
	private final StringProperty selectedRequestId = new SimpleStringProperty("");
	private final StringProperty environmentId = new SimpleStringProperty("");
	private final ChangeListener<Number> activeStructure = (obs, old, now) -> structure.set(structure.get() + 1);
	private final ChangeListener<Number> activeSaves = (obs, old, now) -> saves.set(saves.get() + 1);
	
    private final ReadOnlyIntegerWrapper treeVersion = new ReadOnlyIntegerWrapper(0);
    private final ChangeListener<Number> projectStructure = (obs, old, now) -> bumpTree();
	
	private Runnable flush = () -> {
	};

	@Use
	private RecentProjects recents;

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
		Path normal = path.toAbsolutePath().normalize();
		Optional<OpenProject> already = findByFile(normal);
		if (already.isPresent()) {
			activate(already.get());
			return;
		}
		add(OpenProject.open(normal));
	}

	public void create(Path path) throws IOException {
		Path normal = path.toAbsolutePath().normalize();
		if (findByFile(normal).isPresent()) {
			throw new IOException("That file is already open as a project");
		}
		add(OpenProject.create(normal));
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

    private void add(OpenProject opened) throws IOException {
        for (OpenProject existing : projects) {
            if (existing.id().equals(opened.id())) {
                throw new IOException("A project with the same id is already open: " + existing.file());
            }
        }
        projects.add(opened);
        opened.structureProperty().addListener(projectStructure);
        activate(opened);
        bumpTree();
        if (recents != null) {
            recents.touch(opened.file());
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
            previous.structureProperty().removeListener(activeStructure);
            previous.savesProperty().removeListener(activeSaves);
        }
        if (next == null) {
            selectedRequestId.set("");
            file.set(null);
            project.set(null);
            environmentId.set("");
            active.set(null);
            structure.set(structure.get() + 1);
            return;
        }
        selectedRequestId.bindBidirectional(next.selectedRequestIdProperty());
        file.set(next.file());
        project.bind(next.projectProperty());
        environmentId.bindBidirectional(next.environmentIdProperty());
        next.structureProperty().addListener(activeStructure);
        next.savesProperty().addListener(activeSaves);
        active.set(next);
        structure.set(structure.get() + 1);
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
        if (active.get() == target) {
            activate(projects.isEmpty() ? null : projects.get(Math.min(index, projects.size() - 1)));
        }
        bumpTree();
    }

	private Optional<OpenProject> findById(String projectId) {
		return projects.stream().filter(p -> p.id().equals(projectId)).findFirst();
	}

	private Optional<OpenProject> findByFile(Path file) {
		return projects.stream().filter(p -> p.file().equals(file)).findFirst();
	}
	
    public void selectRequest(String projectId, String requestId) {
        findById(projectId).ifPresent(target -> {
            target.selectedRequestIdProperty().set(requestId == null ? "" : requestId);
            activate(target);
        });
    }

    private void bumpTree() {
        treeVersion.set(treeVersion.get() + 1);
    }
}