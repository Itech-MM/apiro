package io.github.itech_mm.apiro.ui.component;

import java.util.Arrays;
import java.util.List;

import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.AuthType;
import io.github.itech_mm.apiro.ui.editor.VariableField;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
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
	private VariableField usernameField;

	@FXML
	private PasswordField passwordField;

	@FXML
	private PasswordField tokenField;

	@FXML
	private VariableField keyNameField;

	@FXML
	private PasswordField keyValueField;

	private boolean allowInherit = true;

	private final ReadOnlyStringWrapper preview = new ReadOnlyStringWrapper("Inherit from parent");

	@OnInit
	private void init() {
		typeCombo.setItems(FXCollections.observableArrayList(typesFor(allowInherit)));

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
		typeCombo.setValue(defaultType());
		typeCombo.valueProperty().addListener((observable, oldValue, newValue) -> refresh());

		usernameField.valueProperty().addListener((observable, oldValue, newValue) -> refresh());
		keyNameField.valueProperty().addListener((observable, oldValue, newValue) -> refresh());
		List.of(passwordField, tokenField, keyValueField)
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

	public void setAllowInherit(boolean allow) {
		allowInherit = allow;
		AuthType current = typeCombo.getValue();
		typeCombo.setItems(FXCollections.observableArrayList(typesFor(allow)));
		typeCombo.setValue(current == null || (current == AuthType.INHERIT && !allow) ? defaultType() : current);
		refresh();
	}

	public void setConfig(AuthConfig config) {
		AuthConfig source = config == null ? AuthConfig.inherit() : config;
		usernameField.setText(nonNull(source.username()));
		passwordField.setText(nonNull(source.password()));
		tokenField.setText(nonNull(source.token()));
		keyNameField.setText(nonNull(source.keyName()));
		keyValueField.setText(nonNull(source.keyValue()));
		typeCombo.setValue(source.isInherit() && !allowInherit ? AuthType.NONE : source.type());
		refresh();
	}

	public AuthConfig getConfig() {
		AuthType type = typeCombo.getValue() == null ? defaultType() : typeCombo.getValue();
		return new AuthConfig(type, usernameField.getText(), passwordField.getText(),
				tokenField.getText(), keyNameField.getText(), keyValueField.getText());
	}

	private static String nonNull(String value) {
		return value == null ? "" : value;
	}

	private AuthType defaultType() {
		return allowInherit ? AuthType.INHERIT : AuthType.NONE;
	}

	private static List<AuthType> typesFor(boolean allowInherit) {
		return Arrays.stream(AuthType.values()).filter(type -> allowInherit || type != AuthType.INHERIT).toList();
	}

	private void refresh() {
		AuthType type = typeCombo.getValue() == null ? defaultType() : typeCombo.getValue();
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