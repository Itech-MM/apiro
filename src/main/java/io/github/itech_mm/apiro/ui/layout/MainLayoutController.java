package io.github.itech_mm.apiro.ui.layout;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.annotations.FxUse;
import io.github.itech_framework.java_fx.annotations.components.FxScope;
import io.github.itech_framework.java_fx.loader.FxProvider;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_framework.java_fx.ui.layout.LayoutController;
import io.github.itech_mm.apiro.http.RequestRunService;
import io.github.itech_mm.apiro.http.RunCommand;
import io.github.itech_mm.apiro.model.Environment;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.project.ProjectSession;
import io.github.itech_mm.apiro.project.ProjectTree;
import io.github.itech_mm.apiro.project.RecentProjects;
import io.github.itech_mm.apiro.ui.AppCommands;
import io.github.itech_mm.apiro.ui.AppCommands.Command;
import io.github.itech_mm.apiro.ui.component.AuthEditorView;
import io.github.itech_mm.apiro.ui.component.KeyValueTableView;
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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
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
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.StringConverter;

@FxController
public class MainLayoutController extends LayoutController implements Routable {

    private static final String RAIL_ACTIVE = "rail-active";
    private static final String EXTENSION = ".apiro.json";
    private static final double REQUESTS_PANEL_WIDTH = 240.0;
    private static final KeyCodeCombination DUPLICATE_KEYS = new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN);

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
    private Button runButton;

    @FXML
    private Button stopButton;

    @FXML
    private Button searchButton;

    @FXML
    private Button settingsButton;

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
    private Label breadcrumbLabel;

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
        session.structureProperty().addListener((obs, oldValue, newValue) -> rebuild());
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
        
        Platform.runLater(() -> recents.startupProject().ifPresent(path -> {
            if (!session.isOpen()) {
                openPath(path);
            }
        }));
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
        session.projectProperty().addListener((obs, oldValue, newValue) -> refreshBreadcrumb());
        session.selectedRequestIdProperty().addListener((obs, oldValue, newValue) -> refreshBreadcrumb());
        session.structureProperty().addListener((obs, oldValue, newValue) -> refreshBreadcrumb());
        refreshBreadcrumb();
    }

    private void refreshBreadcrumb() {
        Project project = session.project();
        if (project == null) {
            breadcrumbLabel.setText("No project open");
            return;
        }
        String request = session.selectedRequest().map(RequestItem::name).orElse("");
        breadcrumbLabel.setText(request.isBlank() ? project.name() : project.name() + "  /  " + request);
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
            envStatusLabel.setText(newValue == null ? "Env: none" : "Env: " + newValue.name());
        });
    }

    private void setupRequestTree() {
        requestTree.setCellFactory(tree -> new RequestTreeCell());
        requestTree.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (rebuilding) {
                return;
            }
            boolean request = newValue != null && newValue.getValue() instanceof RequestItem;
            syncingSelection = true;
            try {
                session.selectedRequestIdProperty().set(request ? newValue.getValue().id() : "");
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
        Project project = session.project();
        String selectedId = session.selectedRequestIdProperty().get();
        Set<String> collapsed = collapsedIds();
        TreeItem<ProjectItem> restored = null;
        rebuilding = true;
        try {
            requestTree.setRoot(project == null ? null : buildTree(project, collapsed));
            syncEnvironments(project);
            if (project != null && !selectedId.isEmpty()) {
                restored = find(requestTree.getRoot(), selectedId);
            }
            if (restored != null) {
                requestTree.getSelectionModel().select(restored);
            }
        } finally {
            rebuilding = false;
        }
        if (restored == null) {
            session.selectedRequestIdProperty().set("");
        }
    }

    private void syncEnvironments(Project project) {
        syncingEnvironment = true;
        envCombo.setItems(FXCollections.observableArrayList(project == null ? List.of() : project.environments()));
        String active = session.environmentIdProperty().get();
        envCombo.getItems().stream()
                .filter(environment -> environment.id().equals(active))
                .findFirst()
                .ifPresentOrElse(envCombo.getSelectionModel()::select, envCombo.getSelectionModel()::clearSelection);
        syncingEnvironment = false;
        Environment selected = envCombo.getSelectionModel().getSelectedItem();
        envStatusLabel.setText(selected == null ? "Env: none" : "Env: " + selected.name());
    }

    private void refreshValues(Project project) {
        TreeItem<ProjectItem> root = requestTree.getRoot();
        if (project != null && root != null) {
            refresh(root, project);
        }
    }

    private void refresh(TreeItem<ProjectItem> node, Project project) {
        ProjectItem value = node.getValue();
        if (value instanceof RequestItem) {
            project.findItem(value.id()).filter(found -> !found.equals(value)).ifPresent(node::setValue);
        }
        node.getChildren().forEach(child -> refresh(child, project));
    }

    private Set<String> collapsedIds() {
        Set<String> ids = new HashSet<>();
        TreeItem<ProjectItem> root = requestTree.getRoot();
        if (root != null) {
            root.getChildren().forEach(child -> collectCollapsed(child, ids));
        }
        return ids;
    }

    private void collectCollapsed(TreeItem<ProjectItem> node, Set<String> ids) {
        if (node.getValue() instanceof Folder && !node.isExpanded()) {
            ids.add(node.getValue().id());
        }
        node.getChildren().forEach(child -> collectCollapsed(child, ids));
    }

    private TreeItem<ProjectItem> buildTree(Project project, Set<String> collapsed) {
        TreeItem<ProjectItem> root = new TreeItem<>(Folder.of(project.name(), project.items()));
        root.setExpanded(true);
        project.items().forEach(item -> root.getChildren().add(node(item, collapsed)));
        return root;
    }

    private TreeItem<ProjectItem> node(ProjectItem item, Set<String> collapsed) {
        TreeItem<ProjectItem> node = new TreeItem<>(item);
        if (item instanceof Folder folder) {
            node.setExpanded(!collapsed.contains(folder.id()));
            folder.items().forEach(child -> node.getChildren().add(node(child, collapsed)));
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
    }

    private boolean isRoot(TreeItem<ProjectItem> node) {
        return node == null || node == requestTree.getRoot() || node.getValue() == null;
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

    private Optional<String> ask(String title, String label, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(label);
        return dialog.showAndWait().map(String::trim).filter(text -> !text.isEmpty());
    }

    private void addRequest(TreeItem<ProjectItem> target) {
        if (!session.isOpen()) {
            statusLabel.setText("Open or create a project first");
            return;
        }
        ask("New request", "Name", "new-request").ifPresent(name -> {
            RequestItem created = RequestItem.of(name, HttpMethod.GET, "");
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
            session.addItem(folderFor(target), created);
            select(created.id());
        });
    }

    private void openProjectSettings() {
        ProjectSettingsDialog.open(rootPane.getScene().getWindow(), session, authEditors, keyValueTables);
    }
    
    private void renameItem(TreeItem<ProjectItem> node) {
        if (isRoot(node)) {
            return;
        }
        ProjectItem item = node.getValue();
        ask("Rename", "Name", item.name()).ifPresent(name -> session.rename(item.id(), name));
    }

    private void duplicateItem(TreeItem<ProjectItem> node) {
        if (isRoot(node)) {
            return;
        }
        session.duplicate(node.getValue().id()).ifPresent(this::select);
    }

    private void deleteItem(TreeItem<ProjectItem> node) {
        if (isRoot(node)) {
            return;
        }
        ProjectItem item = node.getValue();
        int count = ProjectTree.requestCount(item);
        String message = item instanceof Folder
                ? "Delete folder \"" + item.name() + "\" and " + count + (count == 1 ? " request" : " requests") + " inside it?"
                : "Delete request \"" + item.name() + "\"?";
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(rootPane.getScene().getWindow());
        alert.setTitle("Delete");
        alert.setHeaderText(null);
        alert.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button -> session.delete(item.id()));
    }

    private Drop dropFor(TreeItem<ProjectItem> node, double y, double height) {
        if (node == null || node.getValue() == null) {
            return null;
        }
        if (node == requestTree.getRoot()) {
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
        return ratio < 0.5
                ? new Drop(parent, item.id(), false, Zone.BEFORE)
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
        Path path = toPath(chooser("New project").showSaveDialog(rootPane.getScene().getWindow()));
        if (path == null) {
            return;
        }
        Path target = path.getFileName().toString().endsWith(EXTENSION)
                ? path
                : path.resolveSibling(path.getFileName() + EXTENSION);
        try {
            session.create(target);
            statusLabel.setText("Created " + session.project().name());
        } catch (IOException e) {
            statusLabel.setText("Could not create project: " + e.getMessage());
        }
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
        try {
            session.open(path);
            statusLabel.setText("Opened " + session.project().name());
        } catch (IOException | RuntimeException e) {
            recents.remove(path);
            statusLabel.setText("Could not open project: " + e.getMessage());
        }
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
    private void onAddRequest() {
        addRequest(requestTree.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void onAddFolder() {
        addFolder(requestTree.getSelectionModel().getSelectedItem());
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

    private final class RequestTreeCell extends TreeCell<ProjectItem> {

        private final ContextMenu menu = new ContextMenu();
        private final MenuItem newRequest = new MenuItem("New request");
        private final MenuItem newFolder = new MenuItem("New folder");
        private final SeparatorMenuItem separator = new SeparatorMenuItem();
        private final MenuItem rename = new MenuItem("Rename");
        private final MenuItem duplicate = new MenuItem("Duplicate");
        private final MenuItem delete = new MenuItem("Delete");
        
        private final SeparatorMenuItem settingsSeparator = new SeparatorMenuItem();
        private final MenuItem projectSettings = new MenuItem("Project settings");

        RequestTreeCell() {
            newRequest.setOnAction(event -> addRequest(getTreeItem()));
            newFolder.setOnAction(event -> addFolder(getTreeItem()));
            rename.setOnAction(event -> renameItem(getTreeItem()));
            duplicate.setOnAction(event -> duplicateItem(getTreeItem()));
            delete.setOnAction(event -> deleteItem(getTreeItem()));
            projectSettings.setOnAction(event -> openProjectSettings());
            menu.getItems().addAll(newRequest, newFolder, separator, rename, duplicate, delete, settingsSeparator,
                    projectSettings);
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
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setContextMenu(null);
                return;
            }
            boolean root = getTreeItem() == requestTree.getRoot();
            separator.setVisible(!root);
            rename.setVisible(!root);
            duplicate.setVisible(!root);
            delete.setVisible(!root);
            settingsSeparator.setVisible(root);
            projectSettings.setVisible(root);
            setContextMenu(menu);
            setText(item.name());
            if (item instanceof RequestItem request) {
                Label badge = new Label(request.method().shortLabel());
                badge.getStyleClass().addAll("method-badge", "method-" + request.method().name().toLowerCase());
                setGraphic(badge);
            } else {
                Region icon = new Region();
                icon.getStyleClass().addAll("icon", "icon-folder", "icon-sm", "icon-muted");
                setGraphic(icon);
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
                case BEFORE -> "-fx-border-color: #3574f0 transparent transparent transparent; -fx-border-width: 2 0 0 0;";
                case AFTER -> "-fx-border-color: transparent transparent #3574f0 transparent; -fx-border-width: 0 0 2 0;";
                case INTO -> "-fx-background-color: rgba(53, 116, 240, 0.18);";
                case NONE -> "";
            });
        }
    }
}