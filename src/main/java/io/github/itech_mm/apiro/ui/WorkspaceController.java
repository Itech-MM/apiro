package io.github.itech_mm.apiro.ui;

import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_mm.apiro.model.HttpMethod;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

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

    @OnInit
    private void init() {
        setupMethodCombo();
        setupTabs();
        urlField.setText("{{baseUrl}}/api/v1/users");
        Platform.runLater(this::hideTabContent);
    }

    private void setupMethodCombo() {
        methodCombo.setItems(FXCollections.observableArrayList(HttpMethod.values()));
        methodCombo.setCellFactory(list -> new MethodCell());
        methodCombo.setButtonCell(new MethodCell());
        methodCombo.getSelectionModel().select(HttpMethod.POST);
    }

    private void setupTabs() {
        Tab create = tab("create-user", HttpMethod.POST, false);
        requestTabs.getTabs().addAll(
                create,
                tab("list-users", HttpMethod.GET, false),
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