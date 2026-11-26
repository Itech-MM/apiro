package io.github.itech_mm.apiro.ui.settings;

import io.github.itech_framework.java_fx.ui.dialog.AbstractFxDialog;
import io.github.itech_framework.java_fx.utils.FxThread;
import io.github.itech_framework.java_fx.utils.node.ClickGuard;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Settings dialog frame. Same structure and style classes as the framework ModalDialog, but without the blur on the
 * owner window and with a fade-only open animation, which keeps opening smooth on large windows.
 */
final class SettingsModal extends AbstractFxDialog {

	private static final AtomicReference<SettingsModal> CURRENT = new AtomicReference<>();

	private final VBox container = new VBox();

	private final HBox buttons = new HBox(8);

	private SettingsModal(Stage owner) {
		super(owner, 0);
		configureAnimation(0.99, 0);
		container.getStyleClass().add("alert-container");
		buttons.setAlignment(Pos.CENTER_RIGHT);
		rootPane.getChildren().add(container);
	}

	static SettingsModal create(Stage owner, List<String> stylesheets, Node content, String actionText,
			Runnable action, String closeText) {
		return FxThread.call(() -> {
			SettingsModal dialog = new SettingsModal(owner);
			Button actionButton = new Button(actionText);
			actionButton.getStyleClass().add("dialog-action-button");
			ClickGuard.bind(actionButton, action);
			Button closeButton = new Button(closeText);
			closeButton.getStyleClass().add("dialog-close-button");
			closeButton.setOnAction(event -> dialog.close());
			dialog.buttons.getChildren().addAll(actionButton, closeButton);
			dialog.container.getChildren().addAll(content, dialog.buttons);
			dialog.applyStylesheets(stylesheets);
			return dialog;
		});
	}

	static SettingsModal current() {
		return CURRENT.get();
	}

	void show() {
		showAndWait();
	}

	@Override
	protected void beforeShow() {
		CURRENT.set(this);
	}

	@Override
	protected void afterClosed() {
		CURRENT.compareAndSet(this, null);
	}
}
