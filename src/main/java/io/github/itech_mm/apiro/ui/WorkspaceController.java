package io.github.itech_mm.apiro.ui;

import java.io.IOException;
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
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestBody;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.project.ProjectSession;
import io.github.itech_mm.apiro.project.ProjectTree;
import io.github.itech_mm.apiro.ui.component.AuthEditorView;
import io.github.itech_mm.apiro.ui.component.BodyEditorView;
import io.github.itech_mm.apiro.ui.component.CollapsibleSectionView;
import io.github.itech_mm.apiro.ui.component.KeyValueRow;
import io.github.itech_mm.apiro.ui.component.KeyValueTableView;
import io.github.itech_mm.apiro.ui.component.ResponsePanelView;
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
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

@FxController
public class WorkspaceController implements Routable {

    @FXML
    private TabPane requestTabs;

    @FXML
    private ComboBox<HttpMethod> methodCombo;

    @FXML
    private TextField urlField;

    @FXML
    private Button sendButton;

    @FXML
    private StackPane editorPane;

    @FXML
    private StackPane responsePane;

    @FXML
    private ProgressBar sendProgress;

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

    @OnInit
    private void init() {
        setupMethodCombo();
        setupSections();
        setupResponse();
        setEditable(false);
        Platform.runLater(this::hideTabContent);
        watchEdits();
        requestTabs.getTabs().addListener((ListChangeListener<Tab>) this::onTabsChanged);
        requestTabs.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldTab, newTab) -> activate(stateOf(newTab)));
        session.selectedRequestIdProperty().addListener((obs, oldId, newId) -> open(newId));
        session.structureProperty().addListener((obs, oldValue, newValue) -> syncTabs());
        session.savesProperty().addListener((obs, oldValue, newValue) -> markSaved());
        runService.runningIds().addListener((SetChangeListener<String>) change -> {
            String changed = change.wasAdded() ? change.getElementAdded() : change.getElementRemoved();
            if (active != null && active.id.equals(changed)) {
                updateSend();
                refreshRunning();
            }
        });
        runCommand.register(this::send);
        session.registerFlush(this::commit);
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
        editorPane.getChildren().setAll(scroll);
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
            Optional<RequestItem> item = find(id);
            if (item.isEmpty()) {
                return;
            }
            state = new OpenRequest(item.get());
            state.tab.setUserData(state);
            OpenRequest created = state;
            state.tab.setOnCloseRequest(event -> onCloseRequest(event, created));
            open.put(id, state);
            requestTabs.getTabs().add(state.tab);
        }
        requestTabs.getSelectionModel().select(state.tab);
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

    private void activate(OpenRequest next) {
        if (next == active) {
            return;
        }
        refreshEdits();
        active = next;
        if (next == null) {
            clearEditors();
            session.selectedRequestIdProperty().set("");
            return;
        }
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
        setEditable(true);
        if (next.replay != null) {
            next.replay.run();
        } else {
            response.clear();
        }
        refreshRunning();
        session.selectedRequestIdProperty().set(next.id);
    }

    private void syncTabs() {
        Project project = session.project();
        for (OpenRequest state : List.copyOf(open.values())) {
            Optional<RequestItem> item = find(state.id);
            if (project == null || item.isEmpty()) {
                requestTabs.getTabs().remove(state.tab);
            } else {
                state.title.setText(item.get().name());
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
    }

    private void onCloseRequest(Event event, OpenRequest state) {
        if (state == active) {
            refreshEdits();
        }
        if (!state.dirty) {
            return;
        }
        event.consume();
        AlertDialog.builder()
                .level(AlertDialog.Level.WARNING)
                .title("Unsaved changes")
                .message("\"" + state.title.getText() + "\" has unsaved changes. Save before closing?")
                .addOwner(owner())
                .addButton("Save", () -> {
                    if (saveRequest(state)) {
                        closeTab(state);
                    }
                })
                .addButton("Discard", () -> closeTab(state))
                .addButton("Cancel", null)
                .build()
                .showAsync();
    }

    private void closeTab(OpenRequest state) {
        Platform.runLater(() -> requestTabs.getTabs().remove(state.tab));
    }

    private boolean saveRequest(OpenRequest state) {
        saveOnly = state.id;
        try {
            session.save();
            return true;
        } catch (IOException e) {
            AlertDialog.builder()
                    .level(AlertDialog.Level.ERROR)
                    .title("Could not save")
                    .message(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                    .addOwner(owner())
                    .addButton("OK", null)
                    .build()
                    .showAsync();
            return false;
        } finally {
            saveOnly = "";
        }
    }

    /** Called by the session before it writes the project: moves edited drafts into the project. */
    private void commit() {
        if (!session.isOpen()) {
            return;
        }
        refreshEdits();
        for (OpenRequest state : open.values()) {
            if (!state.dirty || (!saveOnly.isEmpty() && !saveOnly.equals(state.id))) {
                continue;
            }
            find(state.id).ifPresent(item -> session.updateRequest(withName(state.draft, item.name())));
        }
    }

    /** Called after the project file was written: clears the dot of every tab that is now on disk. */
    private void markSaved() {
        for (OpenRequest state : open.values()) {
            if (state.dirty && find(state.id).map(item -> sameContent(item, state.draft)).orElse(false)) {
                state.baseline = state.draft;
                state.showDirty(false);
            }
        }
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
        return new RequestItem(original.id(), original.name(), method, urlField.getText(),
                queryTable.getItems(), headersTable.getItems(), auth,
                keepStored ? original.body() : edited, original.settings());
    }

    private static boolean sameContent(RequestItem a, RequestItem b) {
        return a.method() == b.method()
                && a.url().equals(b.url())
                && a.query().equals(b.query())
                && a.headers().equals(b.headers())
                && Objects.equals(a.auth(), b.auth())
                && a.body().equals(b.body())
                && Objects.equals(a.settings(), b.settings());
    }

    private static RequestItem withName(RequestItem item, String name) {
        return new RequestItem(item.id(), name, item.method(), item.url(), item.query(), item.headers(),
                item.auth(), item.body(), item.settings());
    }

    private Optional<RequestItem> find(String id) {
        Project project = session.project();
        if (project == null) {
            return Optional.empty();
        }
        return project.findItem(id).filter(RequestItem.class::isInstance).map(RequestItem.class::cast);
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
        if (active == null || runService.isRunning(active.id)) {
            return;
        }
        OpenRequest state = active;
        refreshEdits();
        RequestItem edited = withName(state.draft, find(state.id).map(RequestItem::name).orElse(state.draft.name()));
        PreparedRequest prepared;
        try {
            Project preview = ProjectTree.withRequest(session.project(), edited);
            prepared = RequestPreparer.prepare(preview, session.environmentIdProperty().get(), edited.id());
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
        state.replay = null;
        state.startedNanos = System.nanoTime();
        runService.run(state.id, prepared, outcome -> {
            state.replay = () -> show(outcome, unresolved);
            if (state == active) {
                state.replay.run();
            }
        });
    }

    private void refreshRunning() {
        boolean here = active != null && runService.isRunning(active.id);
        sendProgress.setVisible(here);
        if (here) {
            response.showSending(active.startedNanos);
        }
    }

    private void confirmUnresolved(List<String> names, Runnable proceed) {
        String list = names.stream().limit(8).map(name -> "${" + name + "}").collect(Collectors.joining(", "));
        String more = names.size() > 8 ? " and " + (names.size() - 8) + " more" : "";
        AlertDialog.builder()
                .level(AlertDialog.Level.WARNING)
                .title("Undefined variables")
                .message("These variables have no value: " + list + more
                        + ". In headers and body they are sent as written; in the URL the request will fail.")
                .addOwner(owner())
                .addButton("Send anyway", proceed)
                .addButton("Cancel", null)
                .build()
                .showAsync();
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

    // -------------------------------------------------------------- types

    private static final class OpenRequest {

        final String id;
        final Tab tab = new Tab();
        final Label badge = new Label();
        final Label title = new Label();
        final Region dot = new Region();
        RequestItem baseline;
        RequestItem draft;
        boolean dirty;
        Runnable replay;
        long startedNanos;

        OpenRequest(RequestItem item) {
            this.id = item.id();
            this.draft = item;
            Label extension = new Label(".req");
            extension.getStyleClass().add("muted");
            dot.getStyleClass().add("tab-dirty");
            dot.setVisible(false);
            dot.setManaged(false);
            title.setText(item.name());
            showMethod(item.method());
            HBox header = new HBox(6, badge, title, extension, dot);
            header.setAlignment(Pos.CENTER_LEFT);
            tab.setGraphic(header);
            tab.setClosable(true);
        }

        void showMethod(HttpMethod method) {
            badge.setText(method.shortLabel());
            badge.getStyleClass().removeIf(style -> style.startsWith("method-"));
            badge.getStyleClass().addAll("method-badge", "method-" + method.name().toLowerCase());
        }

        void showDirty(boolean on) {
            dirty = on;
            dot.setVisible(on);
            dot.setManaged(on);
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