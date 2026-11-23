package io.github.itech_mm.apiro.ui.component;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class BusyOverlay extends StackPane {

    private final Label label = new Label();

    public BusyOverlay() {
        getStyleClass().add("busy-overlay");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(28, 28);
        spinner.setMaxSize(28, 28);
        VBox box = new VBox(8, spinner, label);
        box.setAlignment(Pos.CENTER);
        box.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        getChildren().add(box);
        setVisible(false);
    }

    public void show(String text) {
        label.setText(text == null ? "" : text);
        setVisible(true);
    }

    public void hide() {
        setVisible(false);
    }
}