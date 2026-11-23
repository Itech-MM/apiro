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
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.annotations.FxUse;
import io.github.itech_framework.java_fx.annotations.components.FxScope;
import io.github.itech_framework.java_fx.loader.FxProvider;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_framework.java_fx.ui.dialog.AlertDialog;
import io.github.itech_mm.apiro.http.FailureKind;
import io.github.itech_mm.apiro.http.RequestPreparer;
import io.github.itech_mm.apiro.http.RequestPreparer.PreparedRequest;
import io.github.itech_mm.apiro.http.RequestRunService;
import io.github.itech_mm.apiro.http.RunCommand;
import io.github.itech_mm.apiro.http.SendOutcome;
import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.BodyType;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestBody;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ResponseData;
import io.github.itech_mm.apiro.project.OpenProject;
import io.github.itech_mm.apiro.project.OpenProjectsStore;
import io.github.itech_mm.apiro.project.ProjectSession;
import io.github.itech_mm.apiro.project.ProjectSlot;
import io.github.itech_mm.apiro.project.ProjectTree;
import io.github.itech_mm.apiro.storage.LastRun;
import io.github.itech_mm.apiro.ui.component.AuthEditorView;
import io.github.itech_mm.apiro.ui.component.BodyEditorView;
import io.github.itech_mm.apiro.ui.component.BusyOverlay;
import io.github.itech_mm.apiro.ui.component.CollapsibleSectionView;
import io.github.itech_mm.apiro.ui.component.KeyValueRow;
import io.github.itech_mm.apiro.ui.component.KeyValueTableView;
import io.github.itech_mm.apiro.ui.component.ResponsePanelView;
import io.github.itech_mm.apiro.ui.component.WelcomePane;
import io.github.itech_mm.apiro.ui.editor.VariableField;
import io.github.itech_mm.apiro.variables.VariableResolver;
import io.github.itech_mm.apiro.variables.VariableScope;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
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
	private ComboBox<HttpMethod> methodCombo;

	@FXML
	private VariableField urlField;

	@FXML
	private Button sendButton;

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
	private HBox crumbBox;

	@FXML
	private Label lastRunLabel;

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
	private RunCommand runCommand;
	
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
		setupSections();
		setupResponse();
		setEditable(false);
		Platform.runLater(this::hideTabContent);
		watchEdits();

		welcome = new WelcomePane(commands);
		welcomeHost.getChildren().setAll(welcome);
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

	private void refreshWelcome() {
		boolean empty = requestTabs.getTabs().isEmpty();
		editorArea.setVisible(!empty);
		editorArea.setManaged(!empty);
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
		open.put(item.id(), state);
		if (index < 0 || index > requestTabs.getTabs().size()) {
			requestTabs.getTabs().add(state.tab);
		} else {
			requestTabs.getTabs().add(index, state.tab);
		}
		return state;
	}

	private void onTabsChanged(ListChangeListener.Change<? extends Tab> change) {
		while (change.next()) {
			if (change.wasRemoved()) {
				for (Tab tab : change.getRemoved()) {
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
					removed.setDrafts(Map.of());
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
				if (open.containsKey(requestId)) {
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
			OpenRequest target = open.get(savedSelected.substring(prefix.length()));
			savedSelected = "";
			if (target != null) {
				requestTabs.getSelectionModel().select(target.tab);
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
			OpenRequest state = stateOf(requestTabs.getTabs().get(i));
			int other = state == null ? -1 : savedOrder.indexOf(state.owner.id() + "/" + state.id);
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
			OpenRequest state = stateOf(tab);
			if (state != null) {
				keys.add(state.owner.id() + "/" + state.id);
			}
		}
		String selected = active == null ? "" : active.owner.id() + "/" + active.id;
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
	}

	private static OpenRequest stateOf(Tab tab) {
		return tab == null ? null : (OpenRequest) tab.getUserData();
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

	private void closeTab(OpenRequest state) {
		Platform.runLater(() -> requestTabs.getTabs().remove(state.tab));
	}

	private boolean saveRequest(OpenRequest state) {
		if (session.activeProperty().get() != state.owner) {
			session.activateProject(state.owner.id());
		}
		saveOnly = state.id;
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
	}

	private void markSaved() {
		for (OpenRequest state : open.values()) {
			if (state.dirty && find(state).map(item -> sameContent(item, state.draft)).orElse(false)) {
				state.baseline = state.draft;
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
				headersTable.getItems(), auth, keepStored ? original.body() : edited, original.settings());
	}

	private static boolean sameContent(RequestItem a, RequestItem b) {
		return a.method() == b.method() && a.url().equals(b.url()) && a.query().equals(b.query())
				&& a.headers().equals(b.headers()) && Objects.equals(a.auth(), b.auth()) && a.body().equals(b.body())
				&& Objects.equals(a.settings(), b.settings());
	}

	private static RequestItem withName(RequestItem item, String name) {
		return new RequestItem(item.id(), name, item.method(), item.url(), item.query(), item.headers(), item.auth(),
				item.body(), item.settings());
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
		sendButton.setDisable(active == null || runService.isRunning(active.id));
	}

	@FXML
	private void onSend() {
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