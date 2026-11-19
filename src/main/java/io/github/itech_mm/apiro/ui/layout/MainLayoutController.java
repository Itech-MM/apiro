package io.github.itech_mm.apiro.ui.layout;

import java.util.List;

import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_framework.java_fx.ui.layout.LayoutController;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

@FxController
public class MainLayoutController extends LayoutController implements Routable {

    private static final String RAIL_ACTIVE = "rail-active";
    private static final double REQUESTS_PANEL_WIDTH = 240.0;

    public record RequestNode(String name, String method) {

        public boolean isFolder() {
            return method == null;
        }

        public String badge() {
            return "DELETE".equals(method) ? "DEL" : method;
        }
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
    private ComboBox<String> projectCombo;

    @FXML
    private ComboBox<String> envCombo;

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
    private Button collapseAllButton;

    @FXML
    private Button requestsRailButton;

    @FXML
    private Button historyRailButton;

    @FXML
    private Button environmentsRailButton;

    @FXML
    private TreeView<RequestNode> requestTree;

    private Button activeRail;

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
        setupProjects();
        setupEnvironments();
        setupRequestTree();
        stopButton.setDisable(true);
        Platform.runLater(() -> {
            setActiveRail(requestsRailButton);
            applyInitialDivider();
        });
    }

    @Override
    public void dispose() {
        requestTree.setRoot(null);
    }

    private void setupProjects() {
        projectCombo.setItems(FXCollections.observableArrayList("petstore-api"));
        projectCombo.getSelectionModel().selectFirst();
    }

    private void setupEnvironments() {
        envCombo.setItems(FXCollections.observableArrayList("Dev", "Staging", "Prod"));
        envCombo.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> envStatusLabel.setText("Env: " + newValue));
        envCombo.getSelectionModel().selectFirst();
    }

    private void setupRequestTree() {
        TreeItem<RequestNode> root = new TreeItem<>(new RequestNode("petstore-api", null));
        root.setExpanded(true);

        TreeItem<RequestNode> users = folder("users",
                request("create-user", "POST"),
                request("list-users", "GET"),
                request("get-user", "GET"),
                request("update-user", "PUT"),
                request("delete-user", "DELETE"));
        users.setExpanded(true);

        TreeItem<RequestNode> orders = folder("orders");
        TreeItem<RequestNode> auth = folder("auth");
        TreeItem<RequestNode> internal = folder("internal",
                request("health", "GET"),
                request("webhook-test", "POST"));
        internal.setExpanded(true);

        root.getChildren().addAll(users, orders, auth, internal);

        requestTree.setCellFactory(tree -> new RequestTreeCell());
        requestTree.setRoot(root);
        requestTree.setShowRoot(true);
        requestTree.getSelectionModel().select(users.getChildren().get(0));
    }

    private TreeItem<RequestNode> folder(String name, TreeItem<RequestNode>... requests) {
        TreeItem<RequestNode> item = new TreeItem<>(new RequestNode(name, null));
        item.getChildren().addAll(requests);
        return item;
    }

    private TreeItem<RequestNode> request(String name, String method) {
        return new TreeItem<>(new RequestNode(name, method));
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

    private void collapse(TreeItem<RequestNode> item) {
        item.setExpanded(false);
        item.getChildren().forEach(this::collapse);
    }

    @FXML
    private void onRun() {
        statusLabel.setText("Running...");
        runButton.setDisable(true);
        stopButton.setDisable(false);
    }

    @FXML
    private void onStop() {
        statusLabel.setText("Stopped");
        runButton.setDisable(false);
        stopButton.setDisable(true);
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
        statusLabel.setText("New request");
    }

    @FXML
    private void onCollapseAll() {
        TreeItem<RequestNode> root = requestTree.getRoot();
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

    private static final class RequestTreeCell extends TreeCell<RequestNode> {

        @Override
        protected void updateItem(RequestNode node, boolean empty) {
            super.updateItem(node, empty);
            if (empty || node == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(node.name());
            if (node.isFolder()) {
                Region icon = new Region();
                icon.getStyleClass().addAll("icon", "icon-folder", "icon-sm", "icon-muted");
                setGraphic(icon);
            } else {
                Label badge = new Label(node.badge());
                badge.getStyleClass().addAll("method-badge", "method-" + node.method().toLowerCase());
                setGraphic(badge);
            }
        }
    }
}