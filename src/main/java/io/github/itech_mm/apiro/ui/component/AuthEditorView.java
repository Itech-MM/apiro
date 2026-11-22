package io.github.itech_mm.apiro.ui.component;

import java.util.List;

import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.AuthType;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

@FxComponent("/views/components/auth-editor.fxml")
public class AuthEditorView {

	private static final String MASK = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";

	@FXML
	@Root
	private VBox root;

	@FXML
	private ComboBox<AuthType> typeCombo;

	@FXML
	private VBox basicBox;

	@FXML
	private VBox bearerBox;

	@FXML
	private VBox apiKeyBox;

	@FXML
	private TextField usernameField;

	@FXML
	private PasswordField passwordField;

	@FXML
	private PasswordField tokenField;

	@FXML
	private TextField keyNameField;

	@FXML
	private PasswordField keyValueField;

	private final ReadOnlyStringWrapper preview = new ReadOnlyStringWrapper("None");

	@OnInit
	private void init() {
		typeCombo.setItems(FXCollections.observableArrayList(AuthType.values()));
		typeCombo.setConverter(new StringConverter<>() {
			@Override
			public String toString(AuthType type) {
				return type == null ? "" : label(type);
			}

			@Override
			public AuthType fromString(String text) {
				return null;
			}
		});
		typeCombo.setValue(AuthType.NONE);
		typeCombo.valueProperty().addListener((observable, oldValue, newValue) -> refresh());

		List.of(usernameField, passwordField, tokenField, keyNameField, keyValueField)
				.forEach(field -> field.textProperty().addListener((observable, oldValue, newValue) -> refresh()));
		refresh();
	}

	public VBox getRoot() {
		return root;
	}

	public ReadOnlyStringProperty previewProperty() {
		return preview.getReadOnlyProperty();
	}

	public String getPreview() {
		return preview.get();
	}

	public void setConfig(AuthConfig config) {
		usernameField.setText(config.username());
		passwordField.setText(config.password());
		tokenField.setText(config.token());
		keyNameField.setText(config.keyName());
		keyValueField.setText(config.keyValue());
		typeCombo.setValue(config.type());
		refresh();
	}

	public AuthConfig getConfig() {
		return new AuthConfig(typeCombo.getValue(), usernameField.getText(), passwordField.getText(),
				tokenField.getText(), keyNameField.getText(), keyValueField.getText());
	}

	private void refresh() {
		AuthType type = typeCombo.getValue() == null ? AuthType.NONE : typeCombo.getValue();
		show(basicBox, type == AuthType.BASIC);
		show(bearerBox, type == AuthType.BEARER);
		show(apiKeyBox, type == AuthType.API_KEY);
		preview.set(buildPreview(type));
	}

	private void show(Node node, boolean visible) {
		node.setVisible(visible);
		node.setManaged(visible);
	}

	private String buildPreview(AuthType type) {
		return switch (type) {
		case INHERIT -> "Inherit from parent";
		case NONE -> "None";
		case BASIC -> ("Basic " + usernameField.getText()).stripTrailing() + masked(passwordField.getText());
		case BEARER -> "Bearer" + masked(tokenField.getText());
		case API_KEY -> ("API Key " + keyNameField.getText()).stripTrailing() + masked(keyValueField.getText());
		};
	}

	private String masked(String value) {
		return value.isBlank() ? " (empty)" : " " + MASK + " (masked)";
	}

	private static String label(AuthType type) {
		return switch (type) {
		case INHERIT -> "Inherit";
		case NONE -> "None";
		case BASIC -> "Basic";
		case BEARER -> "Bearer";
		case API_KEY -> "API Key";
		};
	}
}