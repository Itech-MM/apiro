package io.github.itech_mm.apiro.ui;

import java.util.List;
import java.util.stream.Collectors;

import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.annotations.FxUse;
import io.github.itech_framework.java_fx.annotations.components.FxScope;
import io.github.itech_framework.java_fx.loader.FxProvider;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.BodyType;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.RequestBody;
import io.github.itech_mm.apiro.model.ResponseData;
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

	@FxUse(scope = FxScope.PROTOTYPE)
	private FxProvider<KeyValueTableView> tables;

	@FxUse(scope = FxScope.PROTOTYPE)
	private FxProvider<CollapsibleSectionView> sections;

	@FxUse(scope = FxScope.PROTOTYPE)
	private FxProvider<AuthEditorView> authEditors;

	@FxUse(scope = FxScope.PROTOTYPE)
	private FxProvider<BodyEditorView> bodyEditors;
	
    @FXML
    private StackPane responsePane;

    @FxUse(scope = FxScope.PROTOTYPE)
    private FxProvider<ResponsePanelView> responsePanels;

	@OnInit
	private void init() {
		setupMethodCombo();
		setupTabs();
		setupSections();
        setupResponse();
		urlField.setText("{{baseUrl}}/api/v1/users");
		Platform.runLater(this::hideTabContent);
		bindDividerHover();
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
	            System.out.println("divider bounds = " + divider.getBoundsInParent());
	            divider.setOnMouseEntered(e -> System.out.println("divider enter"));
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
        ResponsePanelView response = responsePanels.create();
        responsePane.getChildren().setAll(response.getRoot());
        response.showResponse(new ResponseData(201, "Created", 214, 120, 1229, 1,
                List.of(KeyValue.of("Content-Type", "application/json"),
                        KeyValue.of("Content-Length", "1229"),
                        KeyValue.of("Location", "/api/v1/users/143"),
                        KeyValue.of("Cache-Control", "no-store"),
                        KeyValue.of("X-Request-Id", "req-7f3a9c")),
                List.of(KeyValue.of("session", "abc123")),
                "{\n  \"id\": 143,\n  \"name\": \"Aung Kyaw\",\n  \"email\": \"aung@example.com\",\n  \"role\": \"member\",\n  \"active\": true,\n  \"createdAt\": \"2026-11-18T09:12:44Z\"\n}"));
    }

	private void setupSections() {
		KeyValueTableView queryTable = tables.create();
		KeyValueTableView headersTable = tables.create();
		CollapsibleSectionView query = section("Query", queryTable, "=");
		CollapsibleSectionView headers = section("Headers", headersTable, ": ");

		queryTable.setItems(List.of(KeyValue.of("page", "1"), KeyValue.of("limit", "20"), KeyValue.of("sort", "name"),
				new KeyValue("debug", "true", false)));
		headersTable.setItems(List.of(KeyValue.of("Accept", "application/json"),
				KeyValue.of("Content-Type", "application/json"), KeyValue.of("X-Trace-Id", "{{traceId}}")));

		AuthEditorView authEditor = authEditors.create();
		CollapsibleSectionView auth = sections.create("Auth", authEditor.getRoot());
		authEditor.previewProperty().addListener((observable, oldValue, newValue) -> auth.setPreview(newValue));
		authEditor.setConfig(AuthConfig.bearer("sample-token-value"));
		auth.setPreview(authEditor.getPreview());

		BodyEditorView bodyEditor = bodyEditors.create();
		CollapsibleSectionView body = sections.create("Body", bodyEditor.getRoot());
		bodyEditor.previewProperty().addListener((observable, oldValue, newValue) -> body.setPreview(newValue));
		bodyEditor.setBody(RequestBody.of(BodyType.JSON,
				"{\n  \"name\": \"Aung Kyaw\",\n  \"email\": \"aung@example.com\",\n  \"role\": \"{{defaultRole}}\",\n  \"active\": true\n}"));
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
		methodCombo.getSelectionModel().select(HttpMethod.POST);
	}

	private void setupTabs() {
		Tab create = tab("create-user", HttpMethod.POST, false);
		requestTabs.getTabs().addAll(create, tab("list-users", HttpMethod.GET, false),
				tab("health", HttpMethod.GET, true));
		requestTabs.getSelectionModel().select(create);
	}

	private Tab tab(String name, HttpMethod method, boolean dirty) {
		Label badge = new Label(method.shortLabel());
		badge.getStyleClass().addAll("method-badge", "method-" + method.name().toLowerCase());

		Label title = new Label(name);
		Label extension = new Label(".req");
		extension.getStyleClass().add("muted");

		HBox header = new HBox(6, badge, title, extension);
		header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
		if (dirty) {
			Region dot = new Region();
			dot.getStyleClass().add("tab-dirty");
			header.getChildren().add(dot);
		}

		Tab tab = new Tab();
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
		sendButton.requestFocus();
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