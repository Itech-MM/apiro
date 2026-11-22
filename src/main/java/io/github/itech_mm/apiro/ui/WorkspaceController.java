package io.github.itech_mm.apiro.ui;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.annotations.FxUse;
import io.github.itech_framework.java_fx.annotations.components.FxScope;
import io.github.itech_framework.java_fx.loader.FxProvider;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_mm.apiro.http.FailureKind;
import io.github.itech_mm.apiro.http.RequestPreparer;
import io.github.itech_mm.apiro.http.RequestPreparer.PreparedRequest;
import io.github.itech_mm.apiro.http.RequestRunService;
import io.github.itech_mm.apiro.http.RunCommand;
import io.github.itech_mm.apiro.http.SendOutcome;
import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.BodyType;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.RequestBody;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.project.ProjectSession;
import io.github.itech_mm.apiro.ui.component.AuthEditorView;
import io.github.itech_mm.apiro.ui.component.BodyEditorView;
import io.github.itech_mm.apiro.ui.component.CollapsibleSectionView;
import io.github.itech_mm.apiro.ui.component.KeyValueRow;
import io.github.itech_mm.apiro.ui.component.KeyValueTableView;
import io.github.itech_mm.apiro.ui.component.ResponsePanelView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

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
    private SplitPane editorSplit;

    @FXML
    private StackPane responsePane;

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
    private String loadedId = "";

    @OnInit
    private void init() {
        setupMethodCombo();
        setupSections();
        setupResponse();
        setEditable(false);
        Platform.runLater(this::hideTabContent);
        bindDividerHover();
        session.selectedRequestIdProperty().addListener((obs, oldValue, newValue) -> onSelectionChanged());
        runService.runningProperty().addListener((obs, was, running) -> sendButton.setDisable(running || loadedId.isEmpty()));
        runCommand.register(this::send);
        session.registerFlush(this::commit);
        session.structureProperty().addListener((obs, oldValue, newValue) -> refreshTab());
        methodCombo.valueProperty().addListener((obs, oldValue, newValue) -> refreshTab());
    }

    private void bindDividerHover() {
        Runnable bind = () -> Platform.runLater(() -> {
            for (Node divider : editorSplit.lookupAll(".split-pane-divider")) {
                Runnable sync = () -> {
                    boolean hot = divider.isHover() || divider.isPressed();
                    if (hot) {
                        if (!divider.getStyleClass().contains("divider-hot")) {
                            divider.getStyleClass().add("divider-hot");
                        }
                    } else {
                        divider.getStyleClass().remove("divider-hot");
                    }
                };
                divider.hoverProperty().addListener((o, was, is) -> sync.run());
                divider.pressedProperty().addListener((o, was, is) -> sync.run());
            }
        });
        if (editorSplit.getSkin() != null) {
            bind.run();
        } else {
            editorSplit.skinProperty().addListener((o, old, skin) -> {
                if (skin != null) {
                    bind.run();
                }
            });
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
        body.setExpanded(true);

        VBox sectionBox = new VBox(query.getRoot(), headers.getRoot(), auth.getRoot(), body.getRoot());
        sectionBox.getStyleClass().add("editor-sections");

        ScrollPane scroll = new ScrollPane(sectionBox);
        scroll.getStyleClass().add("editor-scroll");
        scroll.setFitToWidth(true);
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

    private void onSelectionChanged() {
        commit();
        load(session.selectedRequest());
    }

    private void commit() {
        if (loadedId.isEmpty() || !session.isOpen()) {
            return;
        }
        session.project().findItem(loadedId)
                .filter(RequestItem.class::isInstance)
                .map(RequestItem.class::cast)
                .ifPresent(item -> session.updateRequest(collect(item)));
    }

    private void load(Optional<RequestItem> request) {
        if (request.isEmpty()) {
            loadedId = "";
            requestTabs.getTabs().clear();
            response.clear();
            setEditable(false);
            return;
        }
        RequestItem item = request.get();
        loadedId = item.id();
        methodCombo.getSelectionModel().select(item.method());
        urlField.setText(item.url());
        queryTable.setItems(item.query());
        headersTable.setItems(item.headers());
        authEditor.setConfig(item.auth() == null ? AuthConfig.inherit() : item.auth());
        bodyEditor.setBody(item.body());
        requestTabs.getTabs().setAll(tab(item.name(), item.method()));
        response.clear();
        setEditable(true);
    }

    private RequestItem collect(RequestItem original) {
        RequestBody edited = bodyEditor.getBody();
        boolean keepStored = edited.type() == BodyType.MULTIPART || edited.type() == BodyType.BINARY;
        HttpMethod method = methodCombo.getValue() == null ? original.method() : methodCombo.getValue();
        return new RequestItem(original.id(), original.name(), method, urlField.getText(),
                queryTable.getItems(), headersTable.getItems(), authEditor.getConfig(),
                keepStored ? original.body() : edited, original.settings());
    }

    private void setEditable(boolean on) {
        editorPane.setDisable(!on);
        methodCombo.setDisable(!on);
        urlField.setDisable(!on);
        sendButton.setDisable(!on || runService.runningProperty().get());
    }

    private Tab tab(String name, HttpMethod method) {
        Label badge = new Label(method.shortLabel());
        badge.getStyleClass().addAll("method-badge", "method-" + method.name().toLowerCase());

        Label title = new Label(name);
        Label extension = new Label(".req");
        extension.getStyleClass().add("muted");

        HBox header = new HBox(6, badge, title, extension);
        header.setAlignment(Pos.CENTER_LEFT);

        Tab tab = new Tab();
        tab.setClosable(false);
        tab.setGraphic(header);
        return tab;
    }

    private void hideTabContent() {
        Node header = requestTabs.lookup(".tab-header-area");
        if (header instanceof Region region) {
            requestTabs.minHeightProperty().bind(region.heightProperty());
            requestTabs.prefHeightProperty().bind(region.heightProperty());
            requestTabs.maxHeightProperty().bind(region.heightProperty());
        }
    }

    @FXML
    private void onSend() {
        send();
    }

    private void send() {
        Optional<RequestItem> current = session.selectedRequest();
        if (current.isEmpty() || runService.runningProperty().get()) {
            return;
        }
        RequestItem edited = collect(current.get());
        session.updateRequest(edited);
        PreparedRequest prepared;
        try {
            prepared = RequestPreparer.prepare(session.project(), session.environmentIdProperty().get(), edited.id());
        } catch (RuntimeException e) {
            response.showFailure(FailureKind.INVALID_REQUEST, message(e));
            return;
        }
        String requestId = edited.id();
        List<String> unresolved = prepared.unresolved();
        runService.run(prepared, outcome -> {
            if (requestId.equals(loadedId)) {
                show(outcome, unresolved);
            }
        });
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
    
    private void refreshTab() {
        if (loadedId.isEmpty() || !session.isOpen()) {
            return;
        }
        session.project().findItem(loadedId)
                .filter(RequestItem.class::isInstance)
                .map(RequestItem.class::cast)
                .ifPresent(item -> {
                    HttpMethod method = methodCombo.getValue() == null ? item.method() : methodCombo.getValue();
                    requestTabs.getTabs().setAll(tab(item.name(), method));
                });
    }
}