package io.github.itech_mm.apiro.ui.settings;

import java.util.List;
import javafx.animation.PauseTransition;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

/**
 * IDE style settings layout: title bar, navigation tree, breadcrumb header and page content. The button footer is
 * supplied by the framework dialog and styled from settings.css.
 */
public final class SettingsShell extends VBox {

	private static final double WIDTH = 900;

	private static final double HEIGHT = 520;

	private static final String NAV_SECTION = "PROJECT";

	/** Framework open animation is 300 ms; keep the bitmap cache a little longer, then drop it for crisp text. */
	private static final Duration CACHE_HOLD = Duration.millis(450);

	private final ToggleGroup group = new ToggleGroup();

	private final StackPane content = new StackPane();

	private final Label crumbCurrent = new Label();

	private double dragOffsetX;

	private double dragOffsetY;

	public SettingsShell(String title, String subtitle, List<SettingsPage> pages) {
		getStyleClass().add("settings-shell");
		setPrefSize(WIDTH, HEIGHT);
		HBox body = new HBox(navigation(pages), pageArea(title));
		VBox.setVgrow(body, Priority.ALWAYS);
		getChildren().addAll(header(title, subtitle), body);
		sceneProperty().addListener((obs, old, scene) -> {
			if (scene != null) {
				cacheWhileOpening(scene);
			}
		});
		group.selectedToggleProperty().addListener((obs, old, selected) -> {
			if (selected != null) {
				SettingsPage page = (SettingsPage) selected.getUserData();
				crumbCurrent.setText(page.title());
				content.getChildren().setAll(page.content());
			}
		});
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

	/**
	 * The framework animates the whole dialog root (fade, slide, scale) with a drop shadow on it. Caching the root as a
	 * bitmap during that animation avoids re-rendering every page node on each frame.
	 */
	private static void cacheWhileOpening(Scene scene) {
		Parent root = scene.getRoot();
		root.setCache(true);
		root.setCacheHint(CacheHint.SPEED);
		Runnable release = () -> {
			PauseTransition hold = new PauseTransition(CACHE_HOLD);
			hold.setOnFinished(event -> {
				root.setCache(false);
				root.setCacheHint(CacheHint.DEFAULT);
			});
			hold.play();
		};
		Window window = scene.getWindow();
		if (window == null || window.isShowing()) {
			release.run();
			return;
		}
		EventHandler<WindowEvent> onShown = new EventHandler<>() {
			@Override
			public void handle(WindowEvent event) {
				window.removeEventHandler(WindowEvent.WINDOW_SHOWN, this);
				release.run();
			}
		};
		window.addEventHandler(WindowEvent.WINDOW_SHOWN, onShown);
	}

	private HBox header(String title, String subtitle) {
		Region gear = new Region();
		gear.getStyleClass().addAll("icon", "icon-settings");
		Label titleLabel = new Label(title);
		titleLabel.getStyleClass().add("settings-header-title");
		Label subtitleLabel = new Label(subtitle);
		subtitleLabel.getStyleClass().add("settings-header-subtitle");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		Button close = new Button();
		close.getStyleClass().addAll("icon-button", "icon-button-sm");
		Region closeIcon = new Region();
		closeIcon.getStyleClass().addAll("icon", "icon-sm", "icon-close");
		close.setGraphic(closeIcon);
		close.setFocusTraversable(false);
		close.setOnAction(event -> {
			SettingsModal dialog = SettingsModal.current();
			if (dialog != null) {
				dialog.close();
			}
		});
		HBox header = new HBox(8, gear, titleLabel, subtitleLabel, spacer, close);
		header.getStyleClass().add("settings-header");
		header.setAlignment(Pos.CENTER_LEFT);
		enableDrag(header);
		return header;
	}

	private void enableDrag(HBox header) {
		header.setOnMousePressed(event -> {
			Window window = getScene() == null ? null : getScene().getWindow();
			if (window != null) {
				dragOffsetX = event.getScreenX() - window.getX();
				dragOffsetY = event.getScreenY() - window.getY();
			}
		});
		header.setOnMouseDragged(event -> {
			Window window = getScene() == null ? null : getScene().getWindow();
			if (window != null) {
				window.setX(event.getScreenX() - dragOffsetX);
				window.setY(event.getScreenY() - dragOffsetY);
			}
		});
	}

	private VBox navigation(List<SettingsPage> pages) {
		VBox nav = new VBox(2);
		nav.getStyleClass().add("settings-nav");
		Label section = new Label(NAV_SECTION);
		section.getStyleClass().add("settings-nav-section");
		nav.getChildren().add(section);
		for (SettingsPage page : pages) {
			ToggleButton item = new ToggleButton(page.title());
			item.getStyleClass().add("settings-nav-item");
			item.setMaxWidth(Double.MAX_VALUE);
			item.setToggleGroup(group);
			item.setUserData(page);
			item.setOnAction(event -> item.setSelected(true));
			nav.getChildren().add(item);
		}
		return nav;
	}

	private VBox pageArea(String title) {
		Label crumbRoot = new Label(title);
		crumbRoot.getStyleClass().add("settings-crumb");
		Region chevron = new Region();
		chevron.getStyleClass().addAll("icon", "icon-sm", "icon-muted", "icon-chevron-right");
		crumbCurrent.getStyleClass().add("settings-crumb-current");
		HBox pageHeader = new HBox(6, crumbRoot, chevron, crumbCurrent);
		pageHeader.getStyleClass().add("settings-page-header");
		pageHeader.setAlignment(Pos.CENTER_LEFT);
		content.getStyleClass().add("settings-content");
		content.setAlignment(Pos.TOP_LEFT);
		VBox.setVgrow(content, Priority.ALWAYS);
		VBox area = new VBox(pageHeader, content);
		HBox.setHgrow(area, Priority.ALWAYS);
		return area;
	}
}
