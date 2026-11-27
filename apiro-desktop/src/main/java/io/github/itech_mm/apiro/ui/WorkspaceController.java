package io.github.itech_mm.apiro.ui;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.annotations.FxUse;
import io.github.itech_framework.java_fx.annotations.components.FxScope;
import io.github.itech_framework.java_fx.loader.FxProvider;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_framework.java_fx.ui.dialog.AlertDialog;
import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.BodyType;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestBody;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ResponseData;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.repository.LastRun;
import io.github.itech_mm.apiro.repository.OpenProjectsStore;
import io.github.itech_mm.apiro.service.http.FailureKind;
import io.github.itech_mm.apiro.service.http.RequestExecutor;
import io.github.itech_mm.apiro.service.http.RequestPreparer;
import io.github.itech_mm.apiro.service.http.RequestPreparer.PreparedRequest;
import io.github.itech_mm.apiro.service.http.RequestRunService;
import io.github.itech_mm.apiro.service.http.SendOutcome;
import io.github.itech_mm.apiro.service.project.ProjectTree;
import io.github.itech_mm.apiro.service.scenario.RunStore;
import io.github.itech_mm.apiro.service.scenario.ScenarioEdit;
import io.github.itech_mm.apiro.service.scenario.StepSender;
import io.github.itech_mm.apiro.service.variables.VariableResolver;
import io.github.itech_mm.apiro.service.variables.VariableScope;
import io.github.itech_mm.apiro.session.OpenProject;
import io.github.itech_mm.apiro.session.ProjectSession;
import io.github.itech_mm.apiro.session.ProjectSlot;
import io.github.itech_mm.apiro.ui.component.AuthEditorView;
import io.github.itech_mm.apiro.ui.component.BodyEditorView;
import io.github.itech_mm.apiro.ui.component.BusyOverlay;
import io.github.itech_mm.apiro.ui.component.CollapsibleSectionView;
import io.github.itech_mm.apiro.ui.component.KeyValueRow;
import io.github.itech_mm.apiro.ui.component.KeyValueTableView;
import io.github.itech_mm.apiro.ui.component.ResponsePanelView;
import io.github.itech_mm.apiro.ui.component.WelcomePane;
import io.github.itech_mm.apiro.ui.editor.VariableField;
import io.github.itech_mm.apiro.ui.scenario.ScenarioPane;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TabPane;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

@FxController
public class WorkspaceController implements Routable {

	@FXML
	private TabPane requestTabs;

	@FXML
	private HBox urlGroup;

	@FXML
	private ComboBox<HttpMethod> methodCombo;

	@FXML
	private VariableField urlField;

	@FXML
	private Button sendButton;

	@FXML
	private Region sendIcon;

	@FXML
	private StackPane editorPane;

	@FXML
	private StackPane responsePane;

	@FXML
	private ProgressBar sendProgress;

	@FXML
	private VBox editorArea;

	@FXML
	private StackPane welcomeHost;

	@FXML
	private StackPane scenarioHost;

	@FXML
	private HBox crumbBox;

	@FXML
	private Label lastRunLabel;

	private static final PseudoClass FOCUS_WITHIN = PseudoClass.getPseudoClass("focus-within");

	private static final DateTimeFormatter TODAY_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

	private static final DateTimeFormatter OTHER_DAY_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss");

	@Use
	private AppCommands commands;

	private WelcomePane welcome;

	@FxUse(scope = FxScope.PROTOTYPE)
	private FxProvider<KeyValueTableView> tables;

	@FxUse(scope = FxScope.PROTOTYPE)
	private FxProvider<CollapsibleSectionView> sections;

	@FxUse(scope = FxScope.PROTOTYPE)
	private FxProvider<AuthEditorView> authEditors;

	@FxUse(scope = FxScope.PROTOTYPE)
	private FxProvider<BodyEditorView> bodyEditors;

	@FxUse(scope = FxScope.PROTOTYPE)
	private FxProvider<ResponsePanelView> responsePanels;

	@Use
	private ProjectSession session;

	@Use
	private RequestRunService runService;

	@Use
	private RequestExecutor executor;

	@Use
	private RunCommand runCommand;

	@Use
	private OpenRequests openRequests;

	@Use
	private OpenProjectsStore tabStore;

	private List<String> savedOrder = new ArrayList<>();
	private String savedSelected = "";
	private boolean restoring;

	private KeyValueTableView queryTable;
	private KeyValueTableView headersTable;
	private AuthEditorView authEditor;
	private BodyEditorView bodyEditor;
	private ResponsePanelView response;

	private final Map<String, OpenRequest> open = new LinkedHashMap<>();
	private OpenRequest active;
	private final Map<String, OpenScenario> scenarios = new LinkedHashMap<>();
	private OpenScenario activeScenario;
	private ScenarioPane scenarioPane;
	private boolean loading;
	private boolean checkQueued;
	private String saveOnly = "";

	private final PauseTransition draftTimer = new PauseTransition(Duration.millis(700));

	private static final int HEAVY_ROWS = 6;

	private static final int HEAVY_BODY = 200_000;

	private final BusyOverlay busy = new BusyOverlay();

	@OnInit
	private void init() {
		setupMethodCombo();
		setupUrlGroup();
		setupSections();
		setupResponse();
		setEditable(false);
		Platform.runLater(this::hideTabContent);
		watchEdits();

		welcome = new WelcomePane(commands);
		welcomeHost.getChildren().setAll(welcome);
		scenarioPane = new ScenarioPane(new ScenarioPane.Host() {
			@Override
			public Project project() {
				return activeScenario == null ? session.project() : activeScenario.owner.project();
			}

			@Override
			public String environmentId() {
				return activeScenario == null ? "" : activeScenario.owner.environmentIdProperty().get();
			}

			@Override
			public StepSender sender() {
				return executor::send;
			}

			@Override
			public RunStore runStore() {
				return activeScenario == null ? RunStore.NONE : activeScenario.owner.runStore();
			}

			@Override
			public void save(ScenarioItem scenario) {
				if (activeScenario != null) {
					activeScenario.draft = scenario;
					refreshScenarioDirty(activeScenario);
					draftTimer.playFromStart();
				}
			}
		});
		scenarioHost.getChildren().setAll(scenarioPane);
		requestTabs.getTabs().addListener((ListChangeListener<Tab>) change -> refreshWelcome());
		session.projectProperty().addListener((obs, oldValue, newValue) -> {
			refreshWelcome();
			refreshCrumbs();
		});
		session.activeProperty().addListener((obs, oldProject, newProject) -> onProjectActivated(newProject));
		session.projects().addListener((ListChangeListener<OpenProject>) this::onProjectsChanged);
		refreshWelcome();

		requestTabs.getTabs().addListener((ListChangeListener<Tab>) this::onTabsChanged);
		requestTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
			activate(stateOf(newTab));
			showScenario(scenarioOf(newTab));
			refreshScope();
			refreshCrumbs();
		});

		session.selectedRequestIdProperty().addListener((obs, oldId, newId) -> open(newId));
		session.structureProperty().addListener((obs, oldValue, newValue) -> {
			syncTabs();
			refreshScope();
			refreshCrumbs();
		});
		session.environmentIdProperty().addListener((obs, oldValue, newValue) -> refreshScope());
		session.savesProperty().addListener((obs, oldValue, newValue) -> {
			markSaved();
			refreshScope();
		});
		runService.runningIds().addListener((SetChangeListener<String>) change -> {
			String changed = change.wasAdded() ? change.getElementAdded() : change.getElementRemoved();
			if (active != null && active.id.equals(changed)) {
				updateSend();
				refreshRunning();
			}
		});
		runCommand.register(this::send);
		openRequests.register(this::dirtyRequestIds, this::reloadRequests);
		session.registerFlush(this::commit);

		savedOrder = new ArrayList<>(tabStore.loadTabs());
		savedSelected = tabStore.selectedTab();
		for (OpenProject loaded : List.copyOf(session.projects())) {
			restoreTabs(loaded);
		}
		requestTabs.getTabs().addListener((ListChangeListener<Tab>) change -> persistTabs());
		requestTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> persistTabs());

		draftTimer.setOnFinished(event -> persistDrafts());
	}

	private void setupUrlGroup() {
	    urlGroup.sceneProperty().addListener((obs, oldScene, newScene) -> {
	        if (newScene == null) {
	            urlGroup.pseudoClassStateChanged(FOCUS_WITHIN, false);
	            return;
	        }
	        newScene.focusOwnerProperty().addListener((o, oldFocus, newFocus) ->
	                urlGroup.pseudoClassStateChanged(FOCUS_WITHIN, isDescendant(newFocus, urlGroup)));
	        urlGroup.pseudoClassStateChanged(FOCUS_WITHIN, isDescendant(newScene.getFocusOwner(), urlGroup));
	    });

	    urlGroup.setOnMousePressed(event -> {
	        Object target = event.getTarget();
	        if (!(target instanceof Node node)) {
	            return;
	        }
	        if (!isDescendant(node, methodCombo) && !isDescendant(node, urlField)) {
	            urlField.requestFocus();
	        }
	    });
	}

	private static boolean isDescendant(Node node, Node ancestor) {
	    for (Node current = node; current != null; current = current.getParent()) {
	        if (current == ancestor) {
	            return true;
	        }
	    }
	    return false;
	}

	private void refreshWelcome() {
		boolean empty = requestTabs.getTabs().isEmpty();
		boolean scenario = !empty && activeScenario != null;
		editorArea.setVisible(!empty && !scenario);
		editorArea.setManaged(!empty && !scenario);
		scenarioHost.setVisible(scenario);
		scenarioHost.setManaged(scenario);
		welcomeHost.setVisible(empty);
		welcomeHost.setManaged(empty);
		if (empty) {
			Project project = session.project();
			welcome.refresh(project == null ? null : project.name());
		}
	}

	private void setupResponse() {
		response = responsePanels.create();
		responsePane.getChildren().setAll(response.getRoot());
	}

	private void setupSections() {
		queryTable = tables.create();
		headersTable = tables.create();
		CollapsibleSectionView query = section("Query", queryTable, "=");
		CollapsibleSectionView headers = section("Headers", headersTable, ": ");

		authEditor = authEditors.create();
		CollapsibleSectionView auth = sections.create("Auth", authEditor.getRoot());
		authEditor.previewProperty().addListener((observable, oldValue, newValue) -> auth.setPreview(newValue));
		auth.setPreview(authEditor.getPreview());

		bodyEditor = bodyEditors.create();
		CollapsibleSectionView body = sections.create("Body", bodyEditor.getRoot());
		bodyEditor.previewProperty().addListener((observable, oldValue, newValue) -> body.setPreview(newValue));
		body.setPreview(bodyEditor.getPreview());
		body.setHeaderContent(bodyEditor.getHeader());
		body.getRoot().getStyleClass().add("section-flush");
		body.setExpanded(true);

		VBox sectionBox = new VBox(query.getRoot(), headers.getRoot(), auth.getRoot(), body.getRoot());
		sectionBox.getStyleClass().add("editor-sections");
		VBox.setVgrow(body.getRoot(), Priority.ALWAYS);

		ScrollPane scroll = new ScrollPane(sectionBox);
		scroll.getStyleClass().add("editor-scroll");
		scroll.setFitToWidth(true);
		scroll.setFitToHeight(true);
		editorPane.getChildren().setAll(scroll, busy);
	}

	private CollapsibleSectionView section(String title, KeyValueTableView table, String separator) {
		CollapsibleSectionView section = sections.create(title, table.getRoot());
		Runnable refresh = () -> {
			section.setCount(table.getRows().size());
			section.setPreview(preview(table.getRows(), separator));
		};
		table.getRows().addListener((ListChangeListener<KeyValueRow>) change -> refresh.run());
		refresh.run();
		return section;
	}

	private String preview(List<KeyValueRow> rows, String separator) {
		return rows.stream().filter(row -> !row.getKey().isBlank())
				.map(row -> row.getKey() + separator + row.getValue() + (row.isEnabled() ? "" : " (off)"))
				.collect(Collectors.joining("   "));
	}

	private void setupMethodCombo() {
		methodCombo.setItems(FXCollections.observableArrayList(HttpMethod.values()));
		methodCombo.setCellFactory(list -> new MethodCell());
		methodCombo.setButtonCell(new MethodCell());
		methodCombo.getSelectionModel().select(HttpMethod.GET);
	}

	private void hideTabContent() {
		Node header = requestTabs.lookup(".tab-header-area");
		if (header instanceof Region region) {
			requestTabs.minHeightProperty().bind(region.heightProperty());
			requestTabs.prefHeightProperty().bind(region.heightProperty());
			requestTabs.maxHeightProperty().bind(region.heightProperty());
		}
	}

	// ---------------------------------------------------------------- tabs

	private void open(String id) {
		if (id == null || id.isEmpty() || !session.isOpen()) {
			return;
		}
		if (active != null && active.id.equals(id)) {
			return;
		}
		if (activeScenario != null && activeScenario.id.equals(id)) {
			return;
		}
		OpenScenario existing = scenarios.get(id);
		if (existing != null) {
			requestTabs.getSelectionModel().select(existing.tab);
			return;
		}
		OpenProject scenarioOwner = session.activeProperty().get();
		Optional<ScenarioItem> scenarioItem = findScenario(id);
		if (scenarioOwner != null && scenarioItem.isPresent()) {
			requestTabs.getSelectionModel().select(addScenarioTab(scenarioOwner, scenarioItem.get(), -1, null).tab);
			return;
		}
		OpenRequest state = open.get(id);
		if (state == null) {
			OpenProject owner = session.activeProperty().get();
			Optional<RequestItem> item = find(id);
			if (owner == null || item.isEmpty()) {
				return;
			}
			state = addTab(owner, item.get(), -1, null);
		}
		requestTabs.getSelectionModel().select(state.tab);
	}

	private OpenRequest addTab(OpenProject owner, RequestItem item, int index, RequestItem draft) {
		OpenRequest state = new OpenRequest(owner, item);
		if (draft != null) {
			state.baseline = item;
			state.draft = withName(draft, item.name());
			state.showMethod(state.draft.method());
			state.showDirty(true);
		}
		state.showProject(session.projects().size() > 1);
		state.tab.setUserData(state);
		state.tab.setOnCloseRequest(event -> onCloseRequest(event, state));
		state.tab.setContextMenu(tabMenu(state.tab));
		open.put(item.id(), state);
		if (index < 0 || index > requestTabs.getTabs().size()) {
			requestTabs.getTabs().add(state.tab);
		} else {
			requestTabs.getTabs().add(index, state.tab);
		}
		return state;
	}

	private OpenScenario addScenarioTab(OpenProject owner, ScenarioItem item, int index, ScenarioItem draft) {
		OpenScenario state = new OpenScenario(owner, item);
		if (draft != null) {
			state.draft = ScenarioEdit.withContent(item, draft);
			state.showDirty(true);
		}
		state.showProject(session.projects().size() > 1);
		state.tab.setUserData(state);
		state.tab.setOnCloseRequest(event -> onScenarioCloseRequest(event, state));
		state.tab.setContextMenu(tabMenu(state.tab));
		scenarios.put(item.id(), state);
		if (index < 0 || index > requestTabs.getTabs().size()) {
			requestTabs.getTabs().add(state.tab);
		} else {
			requestTabs.getTabs().add(index, state.tab);
		}
		return state;
	}

	private void showScenario(OpenScenario next) {
		activeScenario = next;
		if (next != null) {
			scenarioView(next).ifPresent(scenarioPane::show);
			if (session.activeProperty().get() == next.owner) {
				session.selectedRequestIdProperty().set(next.id);
			} else {
				session.selectRequest(next.owner.id(), next.id);
			}
		}
		refreshWelcome();
	}

	private Optional<ScenarioItem> findScenario(String id) {
		Project project = session.project();
		if (project == null) {
			return Optional.empty();
		}
		return project.findItem(id).filter(ScenarioItem.class::isInstance).map(ScenarioItem.class::cast);
	}

	private Optional<ScenarioItem> scenarioView(OpenScenario state) {
		return findScenario(state).map(item -> ScenarioEdit.withContent(item, state.draft));
	}

	private void refreshScenarioDirty(OpenScenario state) {
		findScenario(state).ifPresent(item -> state.showDirty(!ScenarioEdit.sameContent(item, state.draft)));
	}

	private Optional<ScenarioItem> findScenario(OpenScenario state) {
		if (!session.projects().contains(state.owner)) {
			return Optional.empty();
		}
		return state.owner.project().findItem(state.id).filter(ScenarioItem.class::isInstance)
				.map(ScenarioItem.class::cast);
	}

	private void onTabsChanged(ListChangeListener.Change<? extends Tab> change) {
		while (change.next()) {
			if (change.wasRemoved()) {
				for (Tab tab : change.getRemoved()) {
					OpenScenario removedScenario = scenarioOf(tab);
					if (removedScenario != null) {
						scenarios.remove(removedScenario.id);
					}
					OpenRequest state = stateOf(tab);
					if (state != null) {
						runService.cancel(state.id);
						open.remove(state.id);
					}
				}
			}
		}
	}

	private void onProjectsChanged(ListChangeListener.Change<? extends OpenProject> change) {
		while (change.next()) {
			if (change.wasRemoved()) {
				for (OpenProject removed : change.getRemoved()) {
					for (OpenRequest state : List.copyOf(open.values())) {
						if (state.owner == removed) {
							requestTabs.getTabs().remove(state.tab);
						}
					}
					for (OpenScenario state : List.copyOf(scenarios.values())) {
						if (state.owner == removed) {
							requestTabs.getTabs().remove(state.tab);
						}
					}
					removed.setDrafts(Map.of());
					removed.setScenarioDrafts(Map.of());
				}
			}
			if (change.wasAdded()) {
				for (OpenProject added : change.getAddedSubList()) {
					restoreTabs(added);
				}
			}
		}
		refreshProjectLabels();
	}

	private void restoreTabs(OpenProject project) {
		if (savedOrder.isEmpty()) {
			return;
		}
		String prefix = project.id() + "/";
		Tab before = requestTabs.getSelectionModel().getSelectedItem();
		boolean any = false;
		restoring = true;
		try {
			for (String key : List.copyOf(savedOrder)) {
				if (!key.startsWith(prefix)) {
					continue;
				}
				String requestId = key.substring(prefix.length());
				if (open.containsKey(requestId) || scenarios.containsKey(requestId)) {
					continue;
				}
				Optional<ScenarioItem> savedScenario = project.project().findItem(requestId)
						.filter(ScenarioItem.class::isInstance).map(ScenarioItem.class::cast);
				if (savedScenario.isPresent()) {
					ScenarioItem scenarioDraft = project.scenarioDrafts().get(requestId);
					boolean scenarioEdited = scenarioDraft != null
							&& !ScenarioEdit.sameContent(scenarioDraft, savedScenario.get());
					addScenarioTab(project, savedScenario.get(), insertIndex(key), scenarioEdited ? scenarioDraft : null);
					any = true;
					continue;
				}
				Optional<RequestItem> item = project.project().findItem(requestId)
						.filter(RequestItem.class::isInstance).map(RequestItem.class::cast);
				if (item.isPresent()) {
					RequestItem draft = project.drafts().get(requestId);
					boolean edited = draft != null && !sameContent(draft, item.get());
					addTab(project, item.get(), insertIndex(key), edited ? draft : null);
					any = true;
				}
			}
		} finally {
			restoring = false;
		}
		if (!any) {
			return;
		}
		if (savedSelected.startsWith(prefix)) {
			String savedId = savedSelected.substring(prefix.length());
			OpenRequest target = open.get(savedId);
			OpenScenario savedTarget = scenarios.get(savedId);
			savedSelected = "";
			if (target != null) {
				requestTabs.getSelectionModel().select(target.tab);
			} else if (savedTarget != null) {
				requestTabs.getSelectionModel().select(savedTarget.tab);
			}
		} else if (before != null) {
			requestTabs.getSelectionModel().select(before);
		}
		persistTabs();
	}

	private int insertIndex(String key) {
		int rank = savedOrder.indexOf(key);
		int position = requestTabs.getTabs().size();
		for (int i = requestTabs.getTabs().size() - 1; i >= 0; i--) {
			String existing = keyOf(requestTabs.getTabs().get(i));
			int other = existing == null ? -1 : savedOrder.indexOf(existing);
			if (other >= 0 && other < rank) {
				return i + 1;
			}
			if (other > rank) {
				position = i;
			}
		}
		return position;
	}

	private void persistTabs() {
		if (restoring || session.slots().stream().anyMatch(slot -> slot.state() == ProjectSlot.State.LOADING)) {
			return;
		}
		List<String> keys = new ArrayList<>();
		for (Tab tab : requestTabs.getTabs()) {
			String key = keyOf(tab);
			if (key != null) {
				keys.add(key);
			}
		}
		String selected = active != null ? active.owner.id() + "/" + active.id
				: activeScenario != null ? activeScenario.owner.id() + "/" + activeScenario.id : "";
		tabStore.saveTabs(keys, selected);
		persistDrafts();
	}

	private void persistDrafts() {
		if (restoring || session.slots().stream().anyMatch(slot -> slot.state() == ProjectSlot.State.LOADING)) {
			return;
		}
		for (OpenProject project : session.projects()) {
			Map<String, RequestItem> drafts = new LinkedHashMap<>();
			for (OpenRequest state : open.values()) {
				if (state.owner == project && state.dirty && state.draft.body().text().length() <= 1_000_000) {
					drafts.put(state.id, state.draft);
				}
			}
			project.setDrafts(drafts);
			Map<String, ScenarioItem> scenarioDrafts = new LinkedHashMap<>();
			for (OpenScenario state : scenarios.values()) {
				if (state.owner == project && state.dirty) {
					scenarioDrafts.put(state.id, state.draft);
				}
			}
			project.setScenarioDrafts(scenarioDrafts);
		}
	}

	private void onProjectActivated(OpenProject next) {
		refreshWelcome();
		refreshScope();
		refreshCrumbs();
		if (next == null) {
			return;
		}
		String wanted = session.selectedRequestIdProperty().get();
		if (wanted != null && !wanted.isEmpty()) {
			open(wanted);
			return;
		}
		if (active != null && active.owner == next) {
			return;
		}
		for (OpenRequest state : open.values()) {
			if (state.owner == next) {
				requestTabs.getSelectionModel().select(state.tab);
				return;
			}
		}
	}

	private void refreshProjectLabels() {
		boolean many = session.projects().size() > 1;
		open.values().forEach(state -> state.showProject(many));
		scenarios.values().forEach(state -> state.showProject(many));
	}

	private void activate(OpenRequest next) {
		if (next == active) {
			return;
		}
		refreshEdits();
		active = next;
		if (next == null) {
			busy.hide();
			clearEditors();
			session.selectedRequestIdProperty().set("");
			return;
		}
		if (!heavy(next.draft)) {
			populate(next);
			return;
		}
		loading = true;
		setEditable(false);
		busy.show("Opening " + next.title.getText() + "...");
		PauseTransition frame = new PauseTransition(Duration.millis(40));
		frame.setOnFinished(event -> {
			if (active == next) {
				populate(next);
			}
		});
		frame.play();
	}

	private void populate(OpenRequest next) {
		loading = true;
		try {
			fill(next.draft);
			if (next.baseline == null) {
				next.baseline = collect(next);
				next.draft = next.baseline;
			}
		} finally {
			loading = false;
		}
		busy.hide();
		setEditable(true);
		if (next.replay != null) {
			next.replay.run();
		} else {
			response.clear();
		}
		refreshRunning();
		syncSession(next);
	}

	private static boolean heavy(RequestItem item) {
		return item.query().size() + item.headers().size() > HEAVY_ROWS || item.body().text().length() > HEAVY_BODY;
	}

	private void syncSession(OpenRequest next) {
		if (session.activeProperty().get() == next.owner) {
			session.selectedRequestIdProperty().set(next.id);
		} else {
			session.selectRequest(next.owner.id(), next.id);
		}
	}

	private void syncTabs() {
		boolean many = session.projects().size() > 1;
		for (OpenRequest state : List.copyOf(open.values())) {
			Optional<RequestItem> item = find(state);
			if (item.isEmpty()) {
				requestTabs.getTabs().remove(state.tab);
			} else {
				state.rename(item.get().name());
				state.showProject(many);
			}
		}
		for (OpenScenario state : List.copyOf(scenarios.values())) {
			Optional<ScenarioItem> item = findScenario(state);
			if (item.isEmpty()) {
				requestTabs.getTabs().remove(state.tab);
			} else {
				state.rename(item.get().name());
				state.showProject(many);
				if (!state.dirty) {
					state.draft = item.get();
				}
				if (state == activeScenario) {
					scenarioPane.show(ScenarioEdit.withContent(item.get(), state.draft));
				}
			}
		}
	}

	private static OpenRequest stateOf(Tab tab) {
		return tab != null && tab.getUserData() instanceof OpenRequest state ? state : null;
	}

	private static OpenScenario scenarioOf(Tab tab) {
		return tab != null && tab.getUserData() instanceof OpenScenario state ? state : null;
	}

	private static String keyOf(Tab tab) {
		OpenRequest request = stateOf(tab);
		if (request != null) {
			return request.owner.id() + "/" + request.id;
		}
		OpenScenario scenario = scenarioOf(tab);
		return scenario == null ? null : scenario.owner.id() + "/" + scenario.id;
	}

	// -------------------------------------------------- dirty and closing

	private void watchEdits() {
		urlField.textProperty().addListener((obs, oldValue, newValue) -> queueCheck());
		methodCombo.valueProperty().addListener((obs, oldValue, newValue) -> queueCheck());
		editorPane.addEventFilter(KeyEvent.KEY_RELEASED, event -> queueCheck());
		editorPane.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> queueCheck());
		editorPane.addEventFilter(ActionEvent.ACTION, event -> queueCheck());
		editorPane.addEventFilter(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, event -> queueCheck());
	}

	private void queueCheck() {
		if (loading || checkQueued) {
			return;
		}
		checkQueued = true;
		Platform.runLater(() -> {
			checkQueued = false;
			refreshEdits();
		});
	}

	private void refreshEdits() {
		if (active == null || loading) {
			return;
		}
		RequestItem edited = collect(active);
		active.draft = edited;
		active.showMethod(edited.method());
		active.showDirty(!sameContent(edited, active.baseline));
		draftTimer.playFromStart();
	}

	private void refreshScope() {
		if (activeScenario != null) {
			scenarioPane.refreshScope();
			return;
		}
		try {
			if (active == null) {
				VariableScope.update(null);
				return;
			}
			VariableScope.update(VariableResolver.forItem(active.owner.project(),
					active.owner.environmentIdProperty().get(), active.id));
		} catch (RuntimeException e) {
			VariableScope.update(null);
		}
	}

	private void onCloseRequest(Event event, OpenRequest state) {
		if (state == active) {
			refreshEdits();
		}
		if (!state.dirty) {
			return;
		}
		event.consume();
		AlertDialog.builder().level(AlertDialog.Level.WARNING).title("Unsaved changes")
				.message("\"" + state.title.getText() + "\" has unsaved changes. Save before closing?")
				.addOwner(owner()).addButton("Save", () -> {
					if (saveRequest(state)) {
						closeTab(state);
					}
				}).addButton("Discard", () -> closeTab(state)).addButton("Cancel", null).build().showAsync();
	}

	// ------------------------------------------------------- tab context menu

	private ContextMenu tabMenu(Tab tab) {
		MenuItem close = new MenuItem("Close");
		MenuItem closeOthers = new MenuItem("Close Others");
		MenuItem closeAll = new MenuItem("Close All");
		MenuItem closeLeft = new MenuItem("Close Tabs to the Left");
		MenuItem closeRight = new MenuItem("Close Tabs to the Right");
		MenuItem closeUnmodified = new MenuItem("Close Unmodified");
		MenuItem save = new MenuItem("Save");
		MenuItem selectInTree = new MenuItem("Select in Requests");
		close.setOnAction(event -> closeTabs(List.of(tab)));
		closeOthers.setOnAction(event -> {
			requestTabs.getSelectionModel().select(tab);
			closeTabs(tabs().stream().filter(other -> other != tab).toList());
		});
		closeAll.setOnAction(event -> closeTabs(tabs()));
		closeLeft.setOnAction(event -> {
			List<Tab> all = tabs();
			requestTabs.getSelectionModel().select(tab);
			closeTabs(all.subList(0, Math.max(0, all.indexOf(tab))));
		});
		closeRight.setOnAction(event -> {
			List<Tab> all = tabs();
			requestTabs.getSelectionModel().select(tab);
			int index = all.indexOf(tab);
			closeTabs(index < 0 ? List.of() : all.subList(index + 1, all.size()));
		});
		closeUnmodified.setOnAction(event -> {
			refreshEdits();
			removeTabs(tabs().stream().filter(other -> !isDirty(other)).toList());
		});
		save.setOnAction(event -> saveTab(tab));
		selectInTree.setOnAction(event -> selectInTree(tab));
		ContextMenu menu = new ContextMenu(close, closeOthers, closeAll, new SeparatorMenuItem(), closeLeft, closeRight,
				closeUnmodified, new SeparatorMenuItem(), save, selectInTree);
		menu.setOnShowing(event -> {
			refreshEdits();
			List<Tab> all = tabs();
			int index = all.indexOf(tab);
			closeOthers.setDisable(all.size() < 2);
			closeLeft.setDisable(index <= 0);
			closeRight.setDisable(index < 0 || index >= all.size() - 1);
			closeUnmodified.setDisable(all.stream().allMatch(this::isDirty));
			save.setDisable(!isDirty(tab));
		});
		return menu;
	}

	private List<Tab> tabs() {
		return List.copyOf(requestTabs.getTabs());
	}

	private boolean isDirty(Tab tab) {
		OpenRequest request = stateOf(tab);
		if (request != null) {
			return request.dirty;
		}
		OpenScenario scenario = scenarioOf(tab);
		return scenario != null && scenario.dirty;
	}

	private String titleOf(Tab tab) {
		OpenRequest request = stateOf(tab);
		if (request != null) {
			return request.title.getText();
		}
		OpenScenario scenario = scenarioOf(tab);
		return scenario != null ? scenario.title.getText() : String.valueOf(tab.getText());
	}

	private boolean saveTab(Tab tab) {
		OpenRequest request = stateOf(tab);
		if (request != null) {
			return saveRequest(request);
		}
		OpenScenario scenario = scenarioOf(tab);
		return scenario == null || saveItem(scenario.owner, scenario.id);
	}

	/** Closes the tabs, asking once about unsaved changes. Cancelling the question closes none of them. */
	private void closeTabs(List<Tab> targets) {
		if (targets.isEmpty()) {
			return;
		}
		refreshEdits();
		List<Tab> dirty = targets.stream().filter(this::isDirty).toList();
		if (dirty.isEmpty()) {
			removeTabs(targets);
			return;
		}
		String message;
		if (dirty.size() == 1) {
			message = "\"" + titleOf(dirty.get(0)) + "\" has unsaved changes. Save before closing?";
		} else {
			String names = dirty.stream().limit(5).map(tab -> "\"" + titleOf(tab) + "\"")
					.collect(Collectors.joining(", "));
			String more = dirty.size() > 5 ? " and " + (dirty.size() - 5) + " more" : "";
			message = dirty.size() + " tabs have unsaved changes: " + names + more + ". Save before closing?";
		}
		AlertDialog.builder().level(AlertDialog.Level.WARNING).title("Unsaved changes").message(message)
				.addOwner(owner()).addButton(dirty.size() == 1 ? "Save" : "Save All", () -> {
					for (Tab tab : dirty) {
						if (!saveTab(tab)) {
							return;
						}
					}
					removeTabs(targets);
				}).addButton("Discard", () -> removeTabs(targets)).addButton("Cancel", null).build().showAsync();
	}

	private void removeTabs(List<Tab> tabs) {
		if (!tabs.isEmpty()) {
			Platform.runLater(() -> requestTabs.getTabs().removeAll(tabs));
		}
	}

	private void selectInTree(Tab tab) {
		OpenRequest request = stateOf(tab);
		OpenScenario scenario = scenarioOf(tab);
		OpenProject owner = request != null ? request.owner : scenario != null ? scenario.owner : null;
		String id = request != null ? request.id : scenario != null ? scenario.id : "";
		if (owner == null) {
			return;
		}
		if (session.activeProperty().get() != owner) {
			session.activateProject(owner.id());
		}
		requestTabs.getSelectionModel().select(tab);
		if (id.equals(session.selectedRequestIdProperty().get())) {
			session.selectedRequestIdProperty().set("");
		}
		session.selectedRequestIdProperty().set(id);
	}

	private void closeTab(OpenRequest state) {
		Platform.runLater(() -> requestTabs.getTabs().remove(state.tab));
	}

	private void onScenarioCloseRequest(Event event, OpenScenario state) {
		if (!state.dirty) {
			return;
		}
		event.consume();
		AlertDialog.builder().level(AlertDialog.Level.WARNING).title("Unsaved changes")
				.message("\"" + state.title.getText() + "\" has unsaved changes. Save before closing?")
				.addOwner(owner()).addButton("Save", () -> {
					if (saveItem(state.owner, state.id)) {
						Platform.runLater(() -> requestTabs.getTabs().remove(state.tab));
					}
				}).addButton("Discard", () -> Platform.runLater(() -> requestTabs.getTabs().remove(state.tab)))
				.addButton("Cancel", null).build().showAsync();
	}

	private boolean saveRequest(OpenRequest state) {
		return saveItem(state.owner, state.id);
	}

	private boolean saveItem(OpenProject owner, String id) {
		if (session.activeProperty().get() != owner) {
			session.activateProject(owner.id());
		}
		saveOnly = id;
		try {
			session.save();
			return true;
		} catch (IOException e) {
			AlertDialog.builder().level(AlertDialog.Level.ERROR).title("Could not save")
					.message(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()).addOwner(owner())
					.addButton("OK", null).build().showAsync();
			return false;
		} finally {
			saveOnly = "";
		}
	}

	private void commit() {
		OpenProject current = session.activeProperty().get();
		if (current == null) {
			return;
		}
		refreshEdits();
		for (OpenRequest state : open.values()) {
			if (state.owner != current || !state.dirty || (!saveOnly.isEmpty() && !saveOnly.equals(state.id))) {
				continue;
			}
			find(state).ifPresent(item -> current.updateRequest(withName(state.draft, item.name())));
		}
		for (OpenScenario state : scenarios.values()) {
			if (state.owner != current || !state.dirty || (!saveOnly.isEmpty() && !saveOnly.equals(state.id))) {
				continue;
			}
			findScenario(state).ifPresent(item -> {
				ScenarioItem merged = ScenarioEdit.withContent(item, state.draft);
				current.updateProject(project -> ProjectTree.withScenario(project, merged));
			});
		}
	}

	private Set<String> dirtyRequestIds() {
		refreshEdits();
		Set<String> ids = new HashSet<>();
		for (OpenRequest state : open.values()) {
			if (state.dirty) {
				ids.add(state.id);
			}
		}
		return ids;
	}

	private void reloadRequests(Set<String> ids) {
		for (String id : ids) {
			OpenRequest state = open.get(id);
			if (state == null || state.dirty) {
				continue;
			}
			Optional<RequestItem> item = find(state);
			if (item.isEmpty()) {
				continue;
			}
			state.baseline = null;
			state.draft = item.get();
			state.showMethod(item.get().method());
			if (state == active) {
				populate(state);
			}
		}
	}

	private void markSaved() {
		for (OpenRequest state : open.values()) {
			if (state.dirty && find(state).map(item -> sameContent(item, state.draft)).orElse(false)) {
				state.baseline = state.draft;
				state.showDirty(false);
			}
		}
		for (OpenScenario state : scenarios.values()) {
			if (state.dirty && findScenario(state).map(item -> ScenarioEdit.sameContent(item, state.draft)).orElse(false)) {
				state.showDirty(false);
			}
		}
		draftTimer.playFromStart();
	}

	// ------------------------------------------------------ editors <-> item

	private void fill(RequestItem item) {
		methodCombo.getSelectionModel().select(item.method());
		urlField.setText(item.url());
		queryTable.setItems(item.query());
		headersTable.setItems(item.headers());
		authEditor.setConfig(item.auth() == null ? AuthConfig.inherit() : item.auth());
		bodyEditor.setBody(item.body());
	}

	private void clearEditors() {
		loading = true;
		try {
			urlField.clear();
			queryTable.setItems(List.of());
			headersTable.setItems(List.of());
			authEditor.setConfig(AuthConfig.inherit());
			bodyEditor.setBody(RequestBody.none());
		} finally {
			loading = false;
		}
		response.clear();
		setEditable(false);
		refreshRunning();
	}

	private RequestItem collect(OpenRequest state) {
		RequestItem original = state.draft;
		RequestBody edited = bodyEditor.getBody();
		boolean keepStored = edited.type() == BodyType.MULTIPART || edited.type() == BodyType.BINARY;
		HttpMethod method = methodCombo.getValue() == null ? original.method() : methodCombo.getValue();
		AuthConfig auth = authEditor.getConfig();
		if (auth.isInherit() && original.auth() == null) {
			auth = null;
		}
		return new RequestItem(original.id(), original.name(), method, urlField.getText(), queryTable.getItems(),
				headersTable.getItems(), auth, keepStored ? original.body() : edited, original.settings(),
				original.manifestEndpointId(), original.imported());
	}

	private static boolean sameContent(RequestItem a, RequestItem b) {
		return a.method() == b.method() && a.url().equals(b.url()) && a.query().equals(b.query())
				&& a.headers().equals(b.headers()) && Objects.equals(a.auth(), b.auth()) && a.body().equals(b.body())
				&& Objects.equals(a.settings(), b.settings());
	}

	private static RequestItem withName(RequestItem item, String name) {
		return new RequestItem(item.id(), name, item.method(), item.url(), item.query(), item.headers(), item.auth(),
				item.body(), item.settings(), item.manifestEndpointId(), item.imported());
	}

	private Optional<RequestItem> find(String id) {
		Project project = session.project();
		if (project == null) {
			return Optional.empty();
		}
		return project.findItem(id).filter(RequestItem.class::isInstance).map(RequestItem.class::cast);
	}

	private Optional<RequestItem> find(OpenRequest state) {
		if (!session.projects().contains(state.owner)) {
			return Optional.empty();
		}
		return state.owner.project().findItem(state.id).filter(RequestItem.class::isInstance)
				.map(RequestItem.class::cast);
	}

	private void setEditable(boolean on) {
		editorPane.setDisable(!on);
		methodCombo.setDisable(!on);
		urlField.setDisable(!on);
		updateSend();
	}

	private void updateSend() {
		boolean running = active != null && runService.isRunning(active.id);
		sendButton.setDisable(active == null);
		sendIcon.getStyleClass().removeAll("icon-play", "icon-stop");
		sendIcon.getStyleClass().add(running ? "icon-stop" : "icon-play");
		sendButton.getStyleClass().remove("send-running");
		if (running) {
			sendButton.getStyleClass().add("send-running");
		}
		Tooltip tip = sendButton.getTooltip();
		if (tip == null) {
			tip = new Tooltip();
			sendButton.setTooltip(tip);
		}
		tip.setText(running ? "Stop request" : "Send request (Ctrl+Enter)");
	}

	@FXML
	private void onSend() {
		if (active != null && runService.isRunning(active.id)) {
			runService.cancel(active.id);
			return;
		}
		send();
	}

	private void send() {
		if (loading) {
			return;
		}

		if (active == null || runService.isRunning(active.id)) {
			return;
		}
		OpenRequest state = active;
		refreshEdits();
		RequestItem edited = withName(state.draft, find(state).map(RequestItem::name).orElse(state.draft.name()));
		PreparedRequest prepared;
		try {
			Project preview = ProjectTree.withRequest(state.owner.project(), edited);
			prepared = RequestPreparer.prepare(preview, state.owner.environmentIdProperty().get(), edited.id());
		} catch (RuntimeException e) {
			state.replay = () -> response.showFailure(FailureKind.INVALID_REQUEST, message(e));
			state.replay.run();
			return;
		}
		if (prepared.unresolved().isEmpty()) {
			dispatch(state, prepared);
			return;
		}
		confirmUnresolved(prepared.unresolved(), () -> dispatch(state, prepared));
	}

	private void dispatch(OpenRequest state, PreparedRequest prepared) {
		if (runService.isRunning(state.id)) {
			return;
		}
		List<String> unresolved = prepared.unresolved();
		OpenProject target = state.owner;
		state.replay = null;
		state.startedNanos = System.nanoTime();
		long started = state.startedNanos;
		runService.run(state.id, prepared, outcome -> {
			state.replay = () -> show(outcome, unresolved);
			record(target, state.id, outcome, started);
			if (state == active) {
				state.replay.run();
				refreshLastRun();
			}
		});
	}

	private void refreshRunning() {
		boolean here = active != null && runService.isRunning(active.id);
		sendProgress.setVisible(here);
		if (here) {
			response.showSending(active.startedNanos);
		}
		refreshLastRun();
	}

	private void confirmUnresolved(List<String> names, Runnable proceed) {
		String list = names.stream().limit(8).map(name -> "${" + name + "}").collect(Collectors.joining(", "));
		String more = names.size() > 8 ? " and " + (names.size() - 8) + " more" : "";
		AlertDialog.builder().level(AlertDialog.Level.WARNING).title("Undefined variables")
				.message("These variables have no value: " + list + more
						+ ". In headers and body they are sent as written; in the URL the request will fail.")
				.addOwner(owner()).addButton("Send anyway", proceed).addButton("Cancel", null).build().showAsync();
	}

	private Stage owner() {
		return sendButton.getScene() != null && sendButton.getScene().getWindow() instanceof Stage stage ? stage : null;
	}

	private void show(SendOutcome outcome, List<String> unresolved) {
		if (outcome instanceof SendOutcome.Done done) {
			response.showResult(done.result(), unresolved);
		} else if (outcome instanceof SendOutcome.Failed failed) {
			response.showFailure(failed.kind(), failed.message());
		} else {
			response.showCancelled();
		}
	}

	private static String message(RuntimeException error) {
		return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
	}

	private void record(OpenProject target, String requestId, SendOutcome outcome, long startedNanos) {
		if (target == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (outcome instanceof SendOutcome.Done done) {
			ResponseData data = done.result().response();
			target.recordRun(requestId, new LastRun(now, data.status(), data.totalMillis()));
		} else if (outcome instanceof SendOutcome.Failed) {
			target.recordRun(requestId, new LastRun(now, 0, (System.nanoTime() - startedNanos) / 1_000_000));
		}
	}

	private void refreshLastRun() {
		if (active == null) {
			lastRunLabel.setText("");
			return;
		}
		if (runService.isRunning(active.id)) {
			lastRunLabel.setText("Running…");
			return;
		}
		lastRunLabel.setText(active.owner.lastRun(active.id).map(WorkspaceController::describe).orElse("Not run yet"));
	}

	private static String describe(LastRun run) {
		ZonedDateTime when = Instant.ofEpochMilli(run.at()).atZone(ZoneId.systemDefault());
		DateTimeFormatter format = when.toLocalDate().equals(LocalDate.now()) ? TODAY_FORMAT : OTHER_DAY_FORMAT;
		String result = run.status() > 0 ? run.status() + " · " + run.millis() + " ms" : "Failed";
		return "Last run " + format.format(when) + " · " + result;
	}

	private void refreshCrumbs() {
		crumbBox.getChildren().clear();
		if (active == null) {
			return;
		}
		Project project = active.owner.project();
		List<String> folders = folderPath(project.items(), active.id);
		if (folders == null) {
			folders = List.of();
		}
		String name = find(active).map(RequestItem::name).orElse(active.draft.name());
		crumbBox.getChildren().add(crumb(project.name(), "crumb"));
		if (folders.size() > 3) {
			crumbBox.getChildren().addAll(crumbSeparator(), crumb("…", "crumb"));
			folders = folders.subList(folders.size() - 2, folders.size());
		}
		for (String folder : folders) {
			crumbBox.getChildren().addAll(crumbSeparator(), crumb(folder, "crumb"));
		}
		crumbBox.getChildren().addAll(crumbSeparator(), crumb(name + ".req", "crumb-current"));
	}

	private static List<String> folderPath(List<ProjectItem> items, String requestId) {
		for (ProjectItem item : items) {
			if (item.id().equals(requestId)) {
				return new ArrayList<>();
			}
			if (item instanceof Folder folder) {
				List<String> inner = folderPath(folder.items(), requestId);
				if (inner != null) {
					inner.add(0, folder.name());
					return inner;
				}
			}
		}
		return null;
	}

	private static Label crumb(String text, String style) {
		Label label = new Label(text);
		label.getStyleClass().add(style);
		return label;
	}

	private static Label crumbSeparator() {
		return crumb("›", "crumb-sep");
	}

	private static final class OpenRequest {

		final String id;
		final OpenProject owner;
		final Tab tab = new Tab();
		final Label badge = new Label();
		final Label title = new Label();
		final Label project = new Label();
		final Region dot = new Region();
		RequestItem baseline;
		RequestItem draft;
		boolean dirty;
		Runnable replay;
		long startedNanos;

		OpenRequest(OpenProject owner, RequestItem item) {
			this.owner = owner;
			this.id = item.id();
			this.draft = item;
			project.getStyleClass().add("muted");
			project.setVisible(false);
			project.setManaged(false);
			dot.getStyleClass().add("tab-dirty");
			dot.setVisible(false);
			dot.setManaged(false);
			title.setText(item.name());
			title.setMaxWidth(180);
			showMethod(item.method());
			HBox header = new HBox(6, badge, title, project, dot);
			header.setAlignment(Pos.CENTER_LEFT);
			tab.setGraphic(header);
			tab.setClosable(true);
		}

		void showProject(boolean on) {
			project.setText("· " + owner.project().name());
			project.setVisible(on);
			project.setManaged(on);
			refreshText();
		}

		void showMethod(HttpMethod method) {
			badge.setText(method.shortLabel());
			badge.getStyleClass().removeIf(style -> style.startsWith("method-"));
			badge.getStyleClass().addAll("method-badge", "method-" + method.name().toLowerCase());
			refreshText();
		}

		void showDirty(boolean on) {
			dirty = on;
			dot.setVisible(on);
			dot.setManaged(on);
		}

		void refreshText() {
			String where = project.isVisible() ? " · " + owner.project().name() : "";
			tab.setText(badge.getText() + " " + title.getText() + ".req" + where);
			tab.setTooltip(new Tooltip(owner.project().name() + "  ›  " + title.getText() + ".req"));
		}

		void rename(String name) {
			title.setText(name);
			refreshText();
		}
	}

	private static final class OpenScenario {

		final String id;
		final OpenProject owner;
		final Tab tab = new Tab();
		final Label badge = new Label("SCN");
		final Label title = new Label();
		final Label project = new Label();
		final Region dot = new Region();
		ScenarioItem draft;
		boolean dirty;

		OpenScenario(OpenProject owner, ScenarioItem item) {
			this.owner = owner;
			this.id = item.id();
			this.draft = item;
			dot.getStyleClass().add("tab-dirty");
			dot.setVisible(false);
			dot.setManaged(false);
			project.getStyleClass().add("muted");
			project.setVisible(false);
			project.setManaged(false);
			badge.getStyleClass().add("method-badge");
			title.setText(item.name());
			title.setMaxWidth(180);
			HBox header = new HBox(6, badge, title, project, dot);
			header.setAlignment(Pos.CENTER_LEFT);
			tab.setGraphic(header);
			tab.setClosable(true);
			refreshText();
		}

		void showDirty(boolean on) {
			dirty = on;
			dot.setVisible(on);
			dot.setManaged(on);
		}

		void showProject(boolean on) {
			project.setText("· " + owner.project().name());
			project.setVisible(on);
			project.setManaged(on);
			refreshText();
		}

		void refreshText() {
			String where = project.isVisible() ? " · " + owner.project().name() : "";
			tab.setText(badge.getText() + " " + title.getText() + ".scn" + where);
			tab.setTooltip(new Tooltip(owner.project().name() + "  ›  " + title.getText() + ".scn"));
		}

		void rename(String name) {
			title.setText(name);
			refreshText();
		}
	}

	private static final class MethodCell extends ListCell<HttpMethod> {

		@Override
		protected void updateItem(HttpMethod method, boolean empty) {
			super.updateItem(method, empty);
			getStyleClass().removeIf(style -> style.startsWith("method-"));
			if (empty || method == null) {
				setText(null);
				return;
			}
			setText(method.shortLabel());
			getStyleClass().addAll("method-text", "method-" + method.name().toLowerCase());
		}
	}
}