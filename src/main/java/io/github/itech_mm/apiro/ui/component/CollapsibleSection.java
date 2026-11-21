package io.github.itech_mm.apiro.ui.component;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class CollapsibleSection extends VBox {

    private final BooleanProperty expanded = new SimpleBooleanProperty(false);
    private final Region chevron = new Region();
    private final Label countLabel = new Label();
    private final Label previewLabel = new Label();
    private final VBox body = new VBox();

    public CollapsibleSection(String title, Node content) {
        getStyleClass().add("section");

        chevron.getStyleClass().addAll("icon", "icon-muted");
        StackPane chevronBox = new StackPane(chevron);
        chevronBox.getStyleClass().add("section-chevron");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");

        countLabel.getStyleClass().add("section-count");

        previewLabel.getStyleClass().add("section-preview");
        previewLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        previewLabel.setMinWidth(0);
        previewLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(previewLabel, Priority.ALWAYS);

        HBox header = new HBox(10, chevronBox, titleLabel, countLabel, previewLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("section-row");
        header.setOnMouseClicked(event -> setExpanded(!isExpanded()));

        body.getStyleClass().add("section-body");
        body.getChildren().add(content);

        getChildren().add(header);
        expanded.addListener((observable, oldValue, newValue) -> render());
        setCount(0);
        render();
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

    private void render() {
        chevron.getStyleClass().removeAll("icon-chevron-right", "icon-chevron-down");
        chevron.getStyleClass().add(isExpanded() ? "icon-chevron-down" : "icon-chevron-right");
        previewLabel.setVisible(!isExpanded());
        previewLabel.setManaged(!isExpanded());
        if (isExpanded()) {
            if (!getChildren().contains(body)) {
                getChildren().add(body);
            }
        } else {
            getChildren().remove(body);
        }
    }
}