package io.github.itech_mm.apiro.ui.settings;

import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class SettingsShell extends HBox {

	private static final double WIDTH = 900;

	private static final double HEIGHT = 480;

	private final ToggleGroup group = new ToggleGroup();

	private final StackPane content = new StackPane();

	public SettingsShell(List<SettingsPage> pages) {
		getStyleClass().add("settings-shell");
		setPrefSize(WIDTH, HEIGHT);
		VBox nav = new VBox(2);
		nav.getStyleClass().add("settings-nav");
		content.getStyleClass().add("settings-content");
		content.setAlignment(Pos.TOP_LEFT);
		HBox.setHgrow(content, Priority.ALWAYS);
		for (SettingsPage page : pages) {
			ToggleButton item = new ToggleButton(page.title());
			item.getStyleClass().add("settings-nav-item");
			item.setMaxWidth(Double.MAX_VALUE);
			item.setToggleGroup(group);
			item.setUserData(page);
			item.setOnAction(event -> item.setSelected(true));
			nav.getChildren().add(item);
		}
		group.selectedToggleProperty().addListener((obs, old, selected) -> {
			if (selected != null) {
				content.getChildren().setAll(((SettingsPage) selected.getUserData()).content());
			}
		});
		getChildren().addAll(nav, content);
		if (!group.getToggles().isEmpty()) {
			group.selectToggle(group.getToggles().get(0));
		}
	}

	public void select(SettingsPage page) {
		for (Toggle toggle : group.getToggles()) {
			if (toggle.getUserData() == page) {
				group.selectToggle(toggle);
				return;
			}
		}
	}
}