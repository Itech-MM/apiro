package io.github.itech_mm.apiro.ui.scenario;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

final class InfoTip {

    private InfoTip() {
    }

    static Label of(String text) {
        Label badge = new Label("?");
        badge.getStyleClass().add("info-tip");
        Tooltip tooltip = new Tooltip(text);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(260);
        tooltip.setShowDelay(Duration.millis(150));
        tooltip.setShowDuration(Duration.seconds(12));
        badge.setTooltip(tooltip);
        return badge;
    }

    static HBox header(String titleText, String tooltipText) {
        Label title = new Label(titleText);
        title.getStyleClass().add("section-title");
        HBox box = new HBox(6, title, of(tooltipText));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
