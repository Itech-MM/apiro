package io.github.itech_mm.apiro.ui.scenario;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

final class StepFloat extends VBox {

    static final double WIDTH = 400;

    private final Node content;
    private final Label title = new Label("Step settings");

    StepFloat(Node content, Runnable onClose) {
        this.content = content;
        getStyleClass().add("step-float");
        setPrefWidth(WIDTH);
        setMinWidth(WIDTH);
        setMaxWidth(WIDTH);
        setMaxHeight(Double.MAX_VALUE);
        StackPane.setAlignment(this, Pos.TOP_RIGHT);

        title.getStyleClass().add("step-float-title");
        Region icon = new Region();
        icon.getStyleClass().addAll("icon", "icon-sm", "icon-close");
        Button close = new Button();
        close.setGraphic(icon);
        close.getStyleClass().addAll("icon-button", "icon-button-sm");
        close.setTooltip(new Tooltip("Close (Esc)"));
        close.setOnAction(event -> onClose.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(8, title, spacer, close);
        head.setAlignment(Pos.CENTER_LEFT);
        head.getStyleClass().add("step-float-head");
        getChildren().add(head);

        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                onClose.run();
                event.consume();
            }
        });
        setOpen(false);
    }

    void setTitle(String text) {
        title.setText(text == null || text.isBlank() ? "Step settings" : text);
    }

    void attach() {
        if (!getChildren().contains(content)) {
            getChildren().add(content);
            VBox.setVgrow(content, Priority.ALWAYS);
        }
    }

    void detach() {
        getChildren().remove(content);
    }

    void setOpen(boolean open) {
        setVisible(open);
        setManaged(open);
    }
}
