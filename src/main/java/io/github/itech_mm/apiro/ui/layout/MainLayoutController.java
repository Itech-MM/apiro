package io.github.itech_mm.apiro.ui.layout;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.utils.DataStorageUtil;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.annotations.FxUse;
import io.github.itech_framework.java_fx.annotations.components.FxScope;
import io.github.itech_framework.java_fx.loader.FxProvider;
import io.github.itech_framework.java_fx.router.Router;
import io.github.itech_framework.java_fx.router.config.RouterConfig;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_framework.java_fx.ui.dialog.AlertDialog;
import io.github.itech_framework.java_fx.ui.dialog.ModalDialog;
import io.github.itech_framework.java_fx.ui.layout.LayoutController;
import io.github.itech_mm.apiro.model.Environment;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.repository.RecentProjects;
import io.github.itech_mm.apiro.service.http.RequestRunService;
import io.github.itech_mm.apiro.service.project.ProjectTree;
import io.github.itech_mm.apiro.session.OpenProject;
import io.github.itech_mm.apiro.session.ProjectSession;
import io.github.itech_mm.apiro.session.ProjectSlot;
import io.github.itech_mm.apiro.ui.AppCommands;
import io.github.itech_mm.apiro.ui.AppCommands.Command;
import io.github.itech_mm.apiro.ui.EditorStyle;
import io.github.itech_mm.apiro.ui.RunCommand;
import io.github.itech_mm.apiro.ui.component.AuthEditorView;
import io.github.itech_mm.apiro.ui.component.KeyValueTableView;
import io.github.itech_mm.apiro.ui.settings.NewProjectDialog;
import io.github.itech_mm.apiro.ui.settings.ProjectSettingsDialog;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import javafx.util.StringConverter;

@FxController
public class MainLayoutController extends LayoutController implements Routable {

	private static final String RAIL_ACTIVE = "rail-active";
	private static final String EXTENSION = ".apiro.json";

	private static final String DANGER_BUTTON = "alert-danger-button";

	private static final String CLOSE_BUTTON = "dialog-close-button";

	private static final String DIALOG_STYLESHEET = MainLayoutController.class.getResource("/static/css/dialog.css")
			.toExternalForm();
	private static final double REQUESTS_PANEL_WIDTH = 240.0;
	private static final KeyCodeCombination DUPLICATE_KEYS = new KeyCodeCombination(KeyCode.D,
			KeyCombination.SHORTCUT_DOWN);

	private enum Zone {
		NONE, BEFORE, AFTER, INTO
	}

	private record Drop(String parentId, String anchorId, boolean after, Zone zone) {
	}

	@FXML
	private BorderPane rootPane;

	@FXML
	private SplitPane mainSplit;

	@FXML
	private StackPane contentContainer;

	@FXML
	private VBox requestsPanel;

	@FXML
	private Label logoLabel;

	@FXML
	private Label panelTitleLabel;

	@FXML
	private Label statusLabel;

	@FXML
	private Label cursorLabel;

	@FXML
	private Label syntaxLabel;

	@FXML
	private Label encodingLabel;

	@FXML
	private Label lineEndingLabel;

	@FXML
	private Label envStatusLabel;

	@FXML
	private Label tlsLabel;

	@FXML
	private Label cookiesLabel;

	@FXML
	private ComboBox<Environment> envCombo;

	@FXML
	private Region envDot;

	@FXML
	private Button runButton;

	@FXML
	private Button stopButton;

	@FXML
	private Button searchButton;

	@FXML
	private Button settingsButton;

	@FXML
	private Button themeButton;

	@FXML
	private Button addRequestButton;

	@FXML
	private Button addFolderButton;

	@FXML
	private Button collapseAllButton;

	@FXML
	private Button requestsRailButton;

	@FXML
	private Button historyRailButton;

	@FXML
	private Button environmentsRailButton;

	@FXML
	private TreeView<ProjectItem> requestTree;

	@FXML
	private Label projectTitleLabel;

	@FXML
	private MenuItem newProjectItem;

	@FXML
	private MenuItem openProjectItem;

	@FXML
	private MenuItem newRequestItem;

	@FXML
	private MenuItem newFolderItem;

	@FXML
	private MenuItem saveItem;

	@FXML
	private MenuItem exitItem;

	@FXML
	private MenuItem runItem;

	@FXML
	private MenuItem stopItem;

	@FXML
	private Menu recentMenu;
	
	@Use
	private Router router;

	@Use
	private ProjectSession session;

	@FxUse(scope = FxScope.PROTOTYPE)
	private FxProvider<AuthEditorView> authEditors;

	@FxUse(scope = FxScope.PROTOTYPE)
	private FxProvider<KeyValueTableView> keyValueTables;

	@Use
	private RequestRunService runService;

	@Use
	private RunCommand runCommand;

	@Use
	private AppCommands commands;

	@Use
	private RecentProjects recents;

	private Button activeRail;
	private boolean syncingEnvironment;
	private boolean rebuilding;
	private boolean syncingSelection;

	private String draggedId;

	@Override
	public void setContent(Parent content) {
		contentContainer.getChildren().setAll(content);
	}

	@Override
	public Parent getContentContainer() {
		return contentContainer;
	}

	@Override
	public void onLayoutLoaded() {
		setupEnvironments();
		setupRequestTree();
		stopButton.setDisable(true);
		addRequestButton.setTooltip(new Tooltip("New request"));
		addFolderButton.setTooltip(new Tooltip("New folder"));
		collapseAllButton.setTooltip(new Tooltip("Collapse all"));
		runButton.setTooltip(new Tooltip("Run request (Ctrl+Enter)"));
		stopButton.setTooltip(new Tooltip("Stop"));
		searchButton.setTooltip(new Tooltip("Find"));
		settingsButton.setTooltip(new Tooltip("Settings"));
		updateThemeButton();
		requestsRailButton.setTooltip(new Tooltip("Requests"));
		historyRailButton.setTooltip(new Tooltip("History"));
		environmentsRailButton.setTooltip(new Tooltip("Environments"));
		Tooltip.install(envStatusLabel, new Tooltip("Switch environment"));
		session.treeVersionProperty().addListener((obs, oldValue, newValue) -> rebuild());
		session.activeProperty().addListener((obs, oldValue, newValue) -> syncEnvironments(session.project()));

		session.projectProperty().addListener((obs, oldValue, newValue) -> refreshValues(newValue));
		runService.runningIds().addListener((SetChangeListener<String>) change -> refreshRunState());
		session.selectedRequestIdProperty().addListener((obs, oldId, newId) -> refreshRunState());
		setupMenu();

		recents.items().addListener((ListChangeListener<Path>) change -> rebuildRecentMenu());
		rebuildRecentMenu();

		refreshRunState();

		Platform.runLater(() -> {
			setActiveRail(requestsRailButton);
			applyInitialDivider();
		});

		Platform.runLater(session::restore);
	}

	@Override
	public void dispose() {
		requestTree.setRoot(null);
	}

	private void setupMenu() {
		commands.register(Command.NEW_PROJECT, this::onNewProject);
		commands.register(Command.OPEN_PROJECT, this::onOpenProject);
		commands.register(Command.NEW_REQUEST, this::onAddRequest);
		commands.register(Command.NEW_FOLDER, this::onAddFolder);
		commands.register(Command.SAVE, this::onSave);
		newProjectItem.setAccelerator(KeyCombination.keyCombination("Shortcut+Shift+N"));
		openProjectItem.setAccelerator(KeyCombination.keyCombination("Shortcut+O"));
		newRequestItem.setAccelerator(KeyCombination.keyCombination("Shortcut+N"));
		saveItem.setAccelerator(KeyCombination.keyCombination("Shortcut+S"));
		runItem.setAccelerator(KeyCombination.keyCombination("Shortcut+Enter"));
		exitItem.setAccelerator(KeyCombination.keyCombination("Shortcut+Q"));
		BooleanBinding noProject = session.projectProperty().isNull();
		saveItem.disableProperty().bind(noProject);
		newRequestItem.disableProperty().bind(noProject);
		newFolderItem.disableProperty().bind(noProject);
		session.projectProperty().addListener((obs, oldValue, newValue) -> refreshProjectTitle());
		session.selectedRequestIdProperty().addListener((obs, oldValue, newValue) -> refreshProjectTitle());
		session.structureProperty().addListener((obs, oldValue, newValue) -> refreshProjectTitle());
		refreshProjectTitle();
	}

	private void refreshProjectTitle() {
		Project project = session.project();
		projectTitleLabel.setText(project == null ? "No project open" : project.name());
	}

	@FXML
	private void onExit() {
		Window window = rootPane.getScene().getWindow();
		window.fireEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSE_REQUEST));
	}

	private void setupEnvironments() {
		envCombo.setConverter(new StringConverter<>() {
			@Override
			public String toString(Environment environment) {
				return environment == null ? "" : environment.name();
			}

			@Override
			public Environment fromString(String text) {
				return null;
			}
		});
		envCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
			if (syncingEnvironment) {
				return;
			}
			session.selectEnvironment(newValue == null ? "" : newValue.id());
			updateEnvIndicator(newValue);
		});
	}

	private void updateEnvIndicator(Environment environment) {
		envStatusLabel.setText(environment == null ? "Env: none" : "Env: " + environment.name());
		envDot.getStyleClass().remove("env-none");
		if (environment == null) {
			envDot.getStyleClass().add("env-none");
		}
	}

	@FXML
	private void onEnvStatusClicked() {
		if (!envCombo.getItems().isEmpty()) {
			envCombo.show();
		}
	}

	private void setupRequestTree() {
		requestTree.setShowRoot(false);
		requestTree.setCellFactory(tree -> new RequestTreeCell());
		requestTree.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
			if (rebuilding) {
				return;
			}
			boolean request = newValue != null
					&& (newValue.getValue() instanceof RequestItem || newValue.getValue() instanceof ScenarioItem);
			String projectId = projectIdOf(newValue);
			syncingSelection = true;
			try {
				if (projectId.isEmpty()) {
					session.selectedRequestIdProperty().set("");
				} else {
					session.selectRequest(projectId, request ? newValue.getValue().id() : "");
				}
			} finally {
				syncingSelection = false;
			}
		});
		session.selectedRequestIdProperty().addListener((obs, oldId, newId) -> {
			if (syncingSelection || rebuilding) {
				return;
			}
			if (newId == null || newId.isEmpty()) {
				requestTree.getSelectionModel().clearSelection();
			} else {
				select(newId);
			}
		});
		requestTree.setOnKeyPressed(event -> {
			TreeItem<ProjectItem> node = requestTree.getSelectionModel().getSelectedItem();
			if (event.getCode() == KeyCode.F2) {
				renameItem(node);
				event.consume();
			} else if (event.getCode() == KeyCode.DELETE) {
				deleteItem(node);
				event.consume();
			} else if (DUPLICATE_KEYS.match(event)) {
				duplicateItem(node);
				event.consume();
			}
		});
	}

	private void rebuild() {
		String selectedId = session.selectedRequestIdProperty().get();
		Set<String> expanded = expandedIds();
		TreeItem<ProjectItem> restored = null;
		rebuilding = true;
		try {
			TreeItem<ProjectItem> root = new TreeItem<>();
			for (OpenProject open : session.projects()) {
				root.getChildren().add(buildTree(open.project(), expanded));
			}
			requestTree.setRoot(root);
			syncEnvironments(session.project());
			if (!selectedId.isEmpty()) {
				restored = find(root, selectedId);
			}
			if (restored != null) {
				for (TreeItem<ProjectItem> parent = restored.getParent(); parent != null; parent = parent.getParent()) {
					parent.setExpanded(true);
				}
				requestTree.getSelectionModel().select(restored);
			}
		} finally {
			rebuilding = false;
		}
		if (restored == null) {
			session.selectedRequestIdProperty().set("");
		} else {
			reveal(restored);
		}
	}

	private void syncEnvironments(Project project) {
		syncingEnvironment = true;
		envCombo.setItems(FXCollections.observableArrayList(project == null ? List.of() : project.environments()));
		String active = session.environmentIdProperty().get();
		envCombo.getItems().stream().filter(environment -> environment.id().equals(active)).findFirst()
				.ifPresentOrElse(envCombo.getSelectionModel()::select, envCombo.getSelectionModel()::clearSelection);
		syncingEnvironment = false;
		updateEnvIndicator(envCombo.getSelectionModel().getSelectedItem());
	}

	private void refreshValues(Project project) {
		TreeItem<ProjectItem> root = requestTree.getRoot();
		if (project == null || root == null) {
			return;
		}
		for (TreeItem<ProjectItem> node : root.getChildren()) {
			if (node.getValue() != null && node.getValue().id().equals(project.id())) {
				refresh(node, project);
			}
		}
	}

	private void refresh(TreeItem<ProjectItem> node, Project project) {
		ProjectItem value = node.getValue();
		if (value instanceof RequestItem) {
			project.findItem(value.id()).filter(found -> !found.equals(value)).ifPresent(node::setValue);
		}
		node.getChildren().forEach(child -> refresh(child, project));
	}

	private Set<String> expandedIds() {
		Set<String> ids = new HashSet<>();
		TreeItem<ProjectItem> root = requestTree.getRoot();
		if (root != null) {
			root.getChildren().forEach(child -> collectExpanded(child, ids));
		}
		return ids;
	}

	private void collectExpanded(TreeItem<ProjectItem> node, Set<String> ids) {
		if (node.getValue() instanceof Folder && node.isExpanded()) {
			ids.add(node.getValue().id());
		}
		node.getChildren().forEach(child -> collectExpanded(child, ids));
	}

	private TreeItem<ProjectItem> buildTree(Project project, Set<String> expanded) {
		Folder value = new Folder(project.id(), project.name(), null, null, null, null, project.items());
		TreeItem<ProjectItem> root = new TreeItem<>(value);
		root.setExpanded(expanded.contains(project.id()));
		project.items().forEach(item -> root.getChildren().add(node(item, expanded)));
		return root;
	}

	private TreeItem<ProjectItem> node(ProjectItem item, Set<String> expanded) {
		TreeItem<ProjectItem> node = new TreeItem<>(item);
		if (item instanceof Folder folder) {
			node.setExpanded(expanded.contains(folder.id()));
			folder.items().forEach(child -> node.getChildren().add(node(child, expanded)));
		}
		return node;
	}

	private TreeItem<ProjectItem> find(TreeItem<ProjectItem> node, String id) {
		if (node.getValue() != null && node.getValue().id().equals(id)) {
			return node;
		}
		for (TreeItem<ProjectItem> child : node.getChildren()) {
			TreeItem<ProjectItem> found = find(child, id);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private void select(String id) {
		TreeItem<ProjectItem> root = requestTree.getRoot();
		TreeItem<ProjectItem> item = root == null ? null : find(root, id);
		if (item == null) {
			return;
		}
		for (TreeItem<ProjectItem> parent = item.getParent(); parent != null; parent = parent.getParent()) {
			parent.setExpanded(true);
		}
		requestTree.getSelectionModel().select(item);
		reveal(item);
	}

	private void reveal(TreeItem<ProjectItem> item) {
		int row = requestTree.getRow(item);
		if (row >= 0) {
			Platform.runLater(() -> requestTree.scrollTo(Math.max(0, row - 2)));
		}
	}

	private boolean isRoot(TreeItem<ProjectItem> node) {
		return node == null || node == requestTree.getRoot() || node.getParent() == requestTree.getRoot();
	}

	private String projectIdOf(TreeItem<ProjectItem> node) {
		TreeItem<ProjectItem> current = node;
		while (current != null && current.getParent() != null && current.getParent() != requestTree.getRoot()) {
			current = current.getParent();
		}
		if (current == null || current.getParent() == null || current.getValue() == null) {
			return "";
		}
		return current.getValue().id();
	}

	private void activateOwner(TreeItem<ProjectItem> node) {
		String projectId = projectIdOf(node);
		if (!projectId.isEmpty()) {
			session.activateProject(projectId);
		}
	}

	private TreeItem<ProjectItem> targetNode() {
		TreeItem<ProjectItem> selected = requestTree.getSelectionModel().getSelectedItem();
		if (selected != null) {
			return selected;
		}
		TreeItem<ProjectItem> root = requestTree.getRoot();
		Project active = session.project();
		if (root == null || active == null) {
			return null;
		}
		return find(root, active.id());
	}

	private String folderFor(TreeItem<ProjectItem> node) {
		if (isRoot(node)) {
			return "";
		}
		ProjectItem value = node.getValue();
		if (value instanceof Folder) {
			return value.id();
		}
		return ProjectTree.parentOf(session.project(), value.id()).orElse("");
	}

	private Stage owner() {
		return rootPane.getScene() != null && rootPane.getScene().getWindow() instanceof Stage stage ? stage : null;
	}

	private Optional<String> ask(String title, String label, String initial) {
		Label heading = new Label(title);
		heading.getStyleClass().add("alert-title");
		Label caption = new Label(label);
		caption.getStyleClass().add("alert-message");
		TextField field = new TextField(initial);
		field.setPrefColumnCount(26);

		String[] answer = new String[1];
		Runnable accept = () -> {
			String text = field.getText() == null ? "" : field.getText().trim();
			if (text.isEmpty()) {
				return;
			}
			answer[0] = text;
			ModalDialog current = ModalDialog.getCurrentDialog();
			if (current != null) {
				current.close();
			}
		};
		field.setOnAction(event -> accept.run());
		Platform.runLater(() -> {
			field.requestFocus();
			field.selectAll();
		});

		ModalDialog.builder().addOwner(owner())
				.stylesheets(MainLayoutController.class.getResource("/static/css/app.css").toExternalForm(),
						MainLayoutController.class.getResource("/static/css/dialog.css").toExternalForm())
				.addContent(new VBox(8, heading, caption, field)).addActionButton("OK", accept).addCloseButton("Cancel")
				.build().show();
		return Optional.ofNullable(answer[0]);
	}

	private void addRequest(TreeItem<ProjectItem> target) {
		if (placeholderOf(target) != null) {
			statusLabel.setText("That project is not loaded");
			return;
		}
		if (!session.isOpen()) {
			statusLabel.setText("Open or create a project first");
			return;
		}
		ask("New request", "Name", "new-request").ifPresent(name -> {
			RequestItem created = RequestItem.of(name, HttpMethod.GET, "");
			activateOwner(target);
			session.addItem(folderFor(target), created);
			select(created.id());
		});
	}

	private void addScenario(TreeItem<ProjectItem> target) {
		if (placeholderOf(target) != null) {
			statusLabel.setText("That project is not loaded");
			return;
		}
		if (!session.isOpen()) {
			statusLabel.setText("Open or create a project first");
			return;
		}
		ask("New scenario", "Name", "new-scenario").ifPresent(name -> {
			ScenarioItem created = ScenarioItem.of(name);
			activateOwner(target);
			session.addItem(folderFor(target), created);
			select(created.id());
		});
	}

	private void addFolder(TreeItem<ProjectItem> target) {
		if (!session.isOpen()) {
			statusLabel.setText("Open or create a project first");
			return;
		}
		ask("New folder", "Name", "new-folder").ifPresent(name -> {
			Folder created = Folder.of(name, List.of());
			activateOwner(target);
			session.addItem(folderFor(target), created);
			select(created.id());
		});
	}

	private void openProjectSettings(TreeItem<ProjectItem> node) {
		activateOwner(node);
		ProjectSettingsDialog.open(rootPane.getScene().getWindow(), session, authEditors, keyValueTables);
	}

	private void renameItem(TreeItem<ProjectItem> node) {
		if (isRoot(node)) {
			return;
		}
		ProjectItem item = node.getValue();
		ask("Rename", "Name", item.name()).ifPresent(name -> {
			activateOwner(node);
			session.rename(item.id(), name);
		});
	}

	private void duplicateItem(TreeItem<ProjectItem> node) {
		if (isRoot(node)) {
			return;
		}
		activateOwner(node);
		session.duplicate(node.getValue().id()).ifPresent(this::select);
	}

	private void deleteItem(TreeItem<ProjectItem> node) {
		if (isRoot(node)) {
			return;
		}
		ProjectItem item = node.getValue();
		int count = ProjectTree.requestCount(item);
		String message = item instanceof Folder
				? "Delete folder \"" + item.name() + "\" and " + count + (count == 1 ? " request" : " requests")
						+ " inside it?"
				: item instanceof ScenarioItem ? "Delete scenario \"" + item.name() + "\"?"
						: "Delete request \"" + item.name() + "\"?";
		AlertDialog.builder().level(AlertDialog.Level.WARNING).title("Delete").message(message).addOwner(owner())
				.addButton("Delete", () -> {
					activateOwner(node);
					session.delete(item.id());
				}).addButton("Cancel", null).build().showAsync();
	}

	private Drop dropFor(TreeItem<ProjectItem> node, double y, double height) {
		if (node == null || node.getValue() == null) {
			return null;
		}
		if (session.project() == null || !projectIdOf(node).equals(session.project().id())) {
			return null;
		}
		if (isRoot(node)) {
			return new Drop("", "", false, Zone.INTO);
		}
		ProjectItem item = node.getValue();
		double ratio = height <= 0 ? 0.5 : y / height;
		String parent = ProjectTree.parentOf(session.project(), item.id()).orElse("");
		if (item instanceof Folder) {
			if (ratio < 0.25) {
				return new Drop(parent, item.id(), false, Zone.BEFORE);
			}
			if (ratio > 0.75) {
				return new Drop(parent, item.id(), true, Zone.AFTER);
			}
			return new Drop(item.id(), "", false, Zone.INTO);
		}
		return ratio < 0.5 ? new Drop(parent, item.id(), false, Zone.BEFORE)
				: new Drop(parent, item.id(), true, Zone.AFTER);
	}

	private boolean acceptable(Drop drop) {
		return drop != null && draggedId != null
				&& ProjectTree.isValidMove(session.project(), draggedId, drop.parentId(), drop.anchorId());
	}

	private void applyInitialDivider() {
		double width = mainSplit.getWidth();
		if (width > 0) {
			mainSplit.setDividerPositions(REQUESTS_PANEL_WIDTH / width);
			return;
		}
		ChangeListener<Number> listener = new ChangeListener<>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				if (newValue.doubleValue() > 0) {
					observable.removeListener(this);
					mainSplit.setDividerPositions(REQUESTS_PANEL_WIDTH / newValue.doubleValue());
				}
			}
		};
		mainSplit.widthProperty().addListener(listener);
	}

	private void selectRail(Button rail, String title) {
		boolean present = mainSplit.getItems().contains(requestsPanel);
		if (present && rail == activeRail) {
			mainSplit.getItems().remove(requestsPanel);
			setActiveRail(null);
			return;
		}
		panelTitleLabel.setText(title);
		if (!present) {
			mainSplit.getItems().add(0, requestsPanel);
			Platform.runLater(this::applyInitialDivider);
		}
		setActiveRail(rail);
	}

	private void setActiveRail(Button active) {
		List<Button> buttons = List.of(requestsRailButton, historyRailButton, environmentsRailButton);
		buttons.forEach(button -> button.getStyleClass().remove(RAIL_ACTIVE));
		if (active != null) {
			active.getStyleClass().add(RAIL_ACTIVE);
		}
		activeRail = active;
	}

	private void collapse(TreeItem<ProjectItem> item) {
		item.setExpanded(false);
		item.getChildren().forEach(this::collapse);
	}

	private void refreshRunState() {
		String id = session.selectedRequestIdProperty().get();
		boolean running = runService.isRunning(id);
		runItem.setDisable(running);
		stopItem.setDisable(!running);
		runButton.setDisable(running);
		stopButton.setDisable(!running);
		int total = runService.runningIds().size();
		statusLabel.setText(total == 0 ? "Ready" : total == 1 ? "Running..." : "Running " + total + " requests...");
	}

	private static FileChooser chooser(String title) {
		FileChooser chooser = new FileChooser();
		chooser.setTitle(title);
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Apiro project", "*" + EXTENSION));
		return chooser;
	}

	private static Path toPath(File file) {
		return file == null ? null : file.toPath();
	}

	@FXML
	private void onSave() {
		if (!session.isOpen()) {
			statusLabel.setText("No project to save");
			return;
		}
		try {
			session.save();
			statusLabel.setText("Saved");
		} catch (IOException e) {
			statusLabel.setText("Could not save: " + e.getMessage());
		}
	}

	@FXML
	private void onNewProject() {
		NewProjectDialog.open(rootPane.getScene().getWindow(), defaultProjectLocation(), authEditors, keyValueTables,
				(file, project) -> {
					session.create(file, project);
					statusLabel.setText("Created " + session.project().name());
				});
	}

	private Path defaultProjectLocation() {
		for (Path recent : recents.items()) {
			Path parent = recent.getParent();
			if (parent != null && Files.isDirectory(parent)) {
				return parent;
			}
		}
		return Path.of(System.getProperty("user.home"));
	}

	@FXML
	private void onOpenProject() {
		Path path = toPath(chooser("Open project").showOpenDialog(rootPane.getScene().getWindow()));
		if (path == null) {
			return;
		}
		openPath(path);
	}

	private void openPath(Path path) {
		statusLabel.setText("Opening " + path.getFileName() + "...");
		session.open(path, slot -> {
			if (slot.state() == ProjectSlot.State.READY) {
				statusLabel.setText("Opened " + slot.name());
			} else {
				recents.remove(path);
				statusLabel.setText("Could not open project: " + slot.message());
			}
		});
	}

	@FXML
	private void onRun() {
		runCommand.run();
	}

	@FXML
	private void onStop() {
		runService.cancel(session.selectedRequestIdProperty().get());
	}

	@FXML
	private void onSearch() {
		statusLabel.setText("Search");
	}

	@FXML
	private void onSettings() {
		statusLabel.setText("Settings");
	}

	@FXML
	private void onToggleTheme() {
		boolean dark = !isDark();
		DataStorageUtil.save(RouterConfig.getDarkModeKey(), dark);
		EditorStyle.darkProperty().set(dark);
		router.refreshTheme();
		updateThemeButton();
	}

	private boolean isDark() {
		Object stored = DataStorageUtil.load(RouterConfig.getDarkModeKey());
		return Boolean.parseBoolean(String.valueOf(stored).replace("\"", ""));
	}

	private void updateThemeButton() {
		themeButton.setTooltip(new Tooltip(isDark() ? "Switch to light mode" : "Switch to dark mode"));
	}

	@FXML
	private void onAddRequest() {
		addRequest(targetNode());
	}

	@FXML
	private void onAddFolder() {
		addFolder(targetNode());
	}

	@FXML
	private void onCollapseAll() {
		TreeItem<ProjectItem> root = requestTree.getRoot();
		if (root != null) {
			root.getChildren().forEach(this::collapse);
		}
	}

	@FXML
	private void onRailRequests() {
		selectRail(requestsRailButton, "Requests");
	}

	@FXML
	private void onRailHistory() {
		selectRail(historyRailButton, "History");
	}

	@FXML
	private void onRailEnvironments() {
		selectRail(environmentsRailButton, "Environments");
	}

	private void rebuildRecentMenu() {
		recentMenu.getItems().clear();
		for (Path path : recents.items()) {
			MenuItem item = new MenuItem(recentLabel(path));
			item.setMnemonicParsing(false);
			item.setOnAction(event -> openPath(path));
			recentMenu.getItems().add(item);
		}
		if (!recents.items().isEmpty()) {
			recentMenu.getItems().add(new SeparatorMenuItem());
			MenuItem clear = new MenuItem("Clear Recent");
			clear.setOnAction(event -> recents.clear());
			recentMenu.getItems().add(clear);
		}
		recentMenu.setDisable(recents.items().isEmpty());
	}

	private static String recentLabel(Path path) {
		String name = path.getFileName().toString();
		String shown = name.endsWith(EXTENSION) ? name.substring(0, name.length() - EXTENSION.length()) : name;
		Path parent = path.getParent();
		return parent == null ? shown : shown + " - " + parent;
	}

	private ProjectSlot placeholderOf(TreeItem<ProjectItem> node) {
		if (node == null || node.getValue() == null || node.getParent() != requestTree.getRoot()) {
			return null;
		}
		return session.slotFor(node.getValue().id()).filter(slot -> slot.state() != ProjectSlot.State.READY)
				.orElse(null);
	}

	private void deleteProject(TreeItem<ProjectItem> node) {
		if (!isRoot(node) || placeholderOf(node) != null) {
			return;
		}
		Optional<ProjectSlot> found = session.slotFor(projectIdOf(node));
		if (found.isEmpty()) {
			return;
		}
		ProjectSlot slot = found.get();
		String message = "What should happen to \"" + slot.name() + "\"?\n\n"
				+ "Remove from workspace closes the project and keeps its file on disk. Unsaved edits in its open "
				+ "requests are discarded.\n\n"
				+ "Delete from disk permanently deletes the project file and its local data.\n\n" + slot.file();
		AlertDialog.builder().level(AlertDialog.Level.WARNING).title("Delete project").message(message)
				.addOwner(owner()).stylesheets(DIALOG_STYLESHEET)
				.addButton("Remove from workspace", () -> {
					session.closeProject(slot.id());
					statusLabel.setText("Removed " + slot.name() + " from workspace");
				}).addButton("Delete from disk", DANGER_BUTTON, () -> Platform.runLater(() -> confirmDeleteFromDisk(slot)))
				.addButton("Cancel", CLOSE_BUTTON, null).build().showAsync();
	}

	private void confirmDeleteFromDisk(ProjectSlot slot) {
		String message = "Permanently delete \"" + slot.name() + "\" from disk?\n\n"
				+ "The project file and the local data stored next to it in the .apiro folder are deleted. "
				+ "This cannot be undone.\n\n" + slot.file();
		AlertDialog.builder().level(AlertDialog.Level.ERROR).title("Delete from disk").message(message)
				.addOwner(owner()).stylesheets(DIALOG_STYLESHEET).addButton("Delete permanently", DANGER_BUTTON, () -> {
					try {
						session.deleteFromDisk(slot.id());
						statusLabel.setText("Deleted " + slot.name() + " from disk");
					} catch (IOException e) {
						statusLabel.setText("Could not delete project: " + e.getMessage());
						Platform.runLater(() -> showDeleteError(slot, e));
					}
				}).addButton("Cancel", CLOSE_BUTTON, null).build().showAsync();
	}

	private void showDeleteError(ProjectSlot slot, IOException error) {
		String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
		AlertDialog.builder().level(AlertDialog.Level.ERROR).title("Could not delete project")
				.message("\"" + slot.name() + "\" was not deleted and stays open: " + detail).addOwner(owner())
				.addButton("OK", null).build().showAsync();
	}

	private void removePlaceholder(TreeItem<ProjectItem> node) {
		ProjectSlot slot = placeholderOf(node);
		if (slot == null) {
			return;
		}
		String message = "Remove \"" + slot.name() + "\" from the workspace? The project file is not deleted:\n"
				+ slot.file();
		AlertDialog.builder().level(AlertDialog.Level.WARNING).title("Remove project").message(message)
				.addOwner(owner()).addButton("Remove", () -> session.removePlaceholder(slot.id()))
				.addButton("Cancel", null).build().showAsync();
	}

	private final class RequestTreeCell extends TreeCell<ProjectItem> {

		private final ContextMenu menu = new ContextMenu();
		private final MenuItem newRequest = new MenuItem("New request");
		private final MenuItem newScenario = new MenuItem("New scenario");
		private final MenuItem newFolder = new MenuItem("New folder");
		private final SeparatorMenuItem separator = new SeparatorMenuItem();
		private final MenuItem rename = new MenuItem("Rename");
		private final MenuItem duplicate = new MenuItem("Duplicate");
		private final MenuItem delete = new MenuItem("Delete");
		private final MenuItem removeFromWorkspace = new MenuItem("Remove project from workspace");

		private final SeparatorMenuItem settingsSeparator = new SeparatorMenuItem();
		private final MenuItem projectSettings = new MenuItem("Project settings");
		private final SeparatorMenuItem deleteSeparator = new SeparatorMenuItem();
		private final MenuItem deleteProject = new MenuItem("Delete project...");

		RequestTreeCell() {
			newRequest.setOnAction(event -> addRequest(getTreeItem()));
			newScenario.setOnAction(event -> addScenario(getTreeItem()));
			newFolder.setOnAction(event -> addFolder(getTreeItem()));
			rename.setOnAction(event -> renameItem(getTreeItem()));
			duplicate.setOnAction(event -> duplicateItem(getTreeItem()));
			delete.setOnAction(event -> deleteItem(getTreeItem()));
			projectSettings.setOnAction(event -> openProjectSettings(getTreeItem()));
			deleteProject.setOnAction(event -> deleteProject(getTreeItem()));
			removeFromWorkspace.setOnAction(event -> removePlaceholder(getTreeItem()));
			newRequest.setGraphic(menuIcon("icon-plus"));
			newScenario.setGraphic(menuIcon("icon-plus"));
			newFolder.setGraphic(menuIcon("icon-folder-plus"));
			rename.setGraphic(menuIcon("icon-edit"));
			duplicate.setGraphic(menuIcon("icon-copy"));
			delete.setGraphic(menuIcon("icon-trash"));
			projectSettings.setGraphic(menuIcon("icon-settings"));
			deleteProject.setGraphic(menuIcon("icon-trash"));
			removeFromWorkspace.setGraphic(menuIcon("icon-close"));
			rename.setAccelerator(new KeyCodeCombination(KeyCode.F2));
			duplicate.setAccelerator(DUPLICATE_KEYS);
			delete.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
			menu.getItems().addAll(newRequest, newScenario, newFolder, separator, rename, duplicate, delete, settingsSeparator,
					projectSettings, deleteSeparator, deleteProject, removeFromWorkspace);
			setOnDragDetected(this::onDragDetected);
			setOnDragOver(this::onDragOver);
			setOnDragDropped(this::onDragDropped);
			setOnDragExited(event -> mark(Zone.NONE));
			setOnDragDone(event -> {
				draggedId = null;
				mark(Zone.NONE);
			});
		}

		@Override
		protected void updateItem(ProjectItem item, boolean empty) {
			super.updateItem(item, empty);
			mark(Zone.NONE);
			getStyleClass().removeAll("project-placeholder", "project-failed");
			setTooltip(null);
			if (empty || item == null) {
				setText(null);
				setGraphic(null);
				setContextMenu(null);
				return;
			}
			ProjectSlot placeholder = placeholderOf(getTreeItem());
			if (placeholder != null) {
				showPlaceholder(placeholder);
				return;
			}
			boolean root = isRoot(getTreeItem());
			newRequest.setVisible(true);
			newScenario.setVisible(true);
			newFolder.setVisible(true);
			removeFromWorkspace.setVisible(false);
			separator.setVisible(!root);
			rename.setVisible(!root);
			duplicate.setVisible(!root);
			delete.setVisible(!root);
			settingsSeparator.setVisible(root);
			projectSettings.setVisible(root);
			deleteSeparator.setVisible(root);
			deleteProject.setVisible(root);
			setContextMenu(menu);
			setText(item.name());
			if (item instanceof RequestItem request) {
				Label tag = new Label(request.method().shortLabel());
				tag.getStyleClass().addAll("method-text", "method-" + request.method().name().toLowerCase());
				setGraphic(tag);
			} else if (item instanceof ScenarioItem) {
				Label tag = new Label("SCN");
				tag.getStyleClass().addAll("method-text", "scenario-text");
				setGraphic(tag);
			} else {
				Region icon = new Region();
				icon.getStyleClass().addAll("icon", "icon-folder", "icon-sm", "icon-muted");
				setGraphic(icon);
			}
		}

		private StackPane menuIcon(String shape) {
			Region icon = new Region();
			icon.getStyleClass().addAll("icon", shape);
			StackPane slot = new StackPane(icon);
			slot.getStyleClass().add("menu-icon");
			return slot;
		}

		private void showPlaceholder(ProjectSlot slot) {
			newRequest.setVisible(false);
			newFolder.setVisible(false);
			separator.setVisible(false);
			rename.setVisible(false);
			duplicate.setVisible(false);
			delete.setVisible(false);
			settingsSeparator.setVisible(false);
			projectSettings.setVisible(false);
			deleteSeparator.setVisible(false);
			deleteProject.setVisible(false);
			removeFromWorkspace.setVisible(true);
			setText(slot.name());
			getStyleClass().add("project-placeholder");
			if (slot.state() == ProjectSlot.State.FAILED) {
				getStyleClass().add("project-failed");
				Region icon = new Region();
				icon.getStyleClass().addAll("icon", "icon-folder", "icon-sm", "icon-muted");
				setGraphic(icon);
				Tooltip tip = new Tooltip(slot.message());
				tip.getStyleClass().add("project-error-tooltip");
				tip.setWrapText(true);
				tip.setMaxWidth(360);
				tip.setShowDelay(Duration.millis(250));
				tip.setHideDelay(Duration.millis(100));
				tip.setShowDuration(Duration.INDEFINITE);
				setTooltip(tip);
				setContextMenu(menu);
			} else {
				ProgressIndicator spinner = new ProgressIndicator();
				spinner.setPrefSize(14, 14);
				spinner.setMaxSize(14, 14);
				spinner.getStyleClass().add("project-loading");
				setGraphic(spinner);
				setContextMenu(null);
			}
		}

		private void onDragDetected(MouseEvent event) {
			TreeItem<ProjectItem> node = getTreeItem();
			if (isRoot(node)) {
				return;
			}
			Dragboard board = startDragAndDrop(TransferMode.MOVE);
			ClipboardContent content = new ClipboardContent();
			content.putString(node.getValue().id());
			board.setContent(content);
			draggedId = node.getValue().id();
			event.consume();
		}

		private void onDragOver(DragEvent event) {
			Drop drop = dropFor(getTreeItem(), event.getY(), getHeight());
			if (acceptable(drop)) {
				event.acceptTransferModes(TransferMode.MOVE);
				mark(drop.zone());
			} else {
				mark(Zone.NONE);
			}
			event.consume();
		}

		private void onDragDropped(DragEvent event) {
			Drop drop = dropFor(getTreeItem(), event.getY(), getHeight());
			boolean done = acceptable(drop);
			if (done) {
				session.move(draggedId, drop.parentId(), drop.anchorId(), drop.after());
			}
			event.setDropCompleted(done);
			event.consume();
		}

		private void mark(Zone zone) {
			setStyle(switch (zone) {
			case BEFORE -> "-fx-border-color: -fx-primary transparent transparent transparent; -fx-border-width: 2 0 0 0;";
			case AFTER -> "-fx-border-color: transparent transparent -fx-primary transparent; -fx-border-width: 0 0 2 0;";
			case INTO -> "-fx-background-color: -app-accent-wash-strong;";
			case NONE -> "";
			});
		}
	}
}