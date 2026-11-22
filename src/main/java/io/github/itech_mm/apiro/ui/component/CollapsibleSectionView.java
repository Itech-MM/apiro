package io.github.itech_mm.apiro.ui.component;

import io.github.itech_framework.core.annotations.constructor.DefaultConstructor;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

@FxComponent("/views/components/collapsible-section.fxml")
public class CollapsibleSectionView {

    @FXML
    @Root
    private VBox root;

    @FXML
    private Region chevron;

    @FXML
    private Label titleLabel;

    @FXML
    private Label countLabel;

    @FXML
    private Label previewLabel;

    @FXML
    private VBox body;

    private final BooleanProperty expanded = new SimpleBooleanProperty(false);
    private String title = "";
    private Node content;

    @DefaultConstructor
    public CollapsibleSectionView() {
    }

    public CollapsibleSectionView(String title, Node content) {
        this.title = title;
        this.content = content;
    }

    @OnInit
    private void init() {
        titleLabel.setText(title);
        if (content != null) {
            body.getChildren().setAll(content);
        }
        expanded.addListener((observable, oldValue, newValue) -> render());
        setCount(0);
        render();
    }

    public VBox getRoot() {
        return root;
    }

    public boolean isExpanded() {
        return expanded.get();
    }

    public void setExpanded(boolean value) {
        expanded.set(value);
    }

    public BooleanProperty expandedProperty() {
        return expanded;
    }

    public void setCount(int count) {
        countLabel.setText(String.valueOf(count));
        boolean show = count > 0;
        countLabel.setVisible(show);
        countLabel.setManaged(show);
    }

    public void setPreview(String text) {
        previewLabel.setText(text == null ? "" : text);
    }

    @FXML
    private void onToggle() {
        setExpanded(!isExpanded());
    }

    private void render() {
        chevron.getStyleClass().removeAll("icon-chevron-right", "icon-chevron-down");
        chevron.getStyleClass().add(isExpanded() ? "icon-chevron-down" : "icon-chevron-right");
        previewLabel.setVisible(!isExpanded());
        previewLabel.setManaged(!isExpanded());
        if (isExpanded()) {
            if (!root.getChildren().contains(body)) {
                root.getChildren().add(body);
            }
        } else {
            root.getChildren().remove(body);
        }
    }
}