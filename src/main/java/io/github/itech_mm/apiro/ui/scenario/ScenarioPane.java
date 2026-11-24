package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_mm.apiro.model.ScenarioItem;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class ScenarioPane extends VBox {

    private final Label title = new Label();
    private final Label summary = new Label();

    public ScenarioPane() {
        super(6);
        getStyleClass().add("scenario-pane");
        title.getStyleClass().add("scenario-title");
        summary.getStyleClass().add("muted");
        setAlignment(Pos.CENTER);
        getChildren().addAll(title, summary);
    }

    public void show(ScenarioItem scenario) {
        title.setText(scenario.name());
        int steps = scenario.steps().size();
        summary.setText(steps == 0 ? "No steps yet" : steps + (steps == 1 ? " step" : " steps"));
    }
}
