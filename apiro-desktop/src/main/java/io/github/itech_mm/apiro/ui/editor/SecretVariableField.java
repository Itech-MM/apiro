package io.github.itech_mm.apiro.ui.editor;

import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

public class SecretVariableField extends HBox {

    private final VariableField plain = new VariableField();

    private final PasswordField hidden = new PasswordField();

    private final CheckBox hide = new CheckBox("Hide");

    public SecretVariableField() {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);
        plain.getStyleClass().add("kv-field");
        hidden.getStyleClass().add("kv-field");
        hidden.textProperty().bindBidirectional(plain.valueProperty());
        plain.visibleProperty().bind(hide.selectedProperty().not());
        plain.managedProperty().bind(plain.visibleProperty());
        hidden.visibleProperty().bind(hide.selectedProperty());
        hidden.managedProperty().bind(hidden.visibleProperty());
        StackPane field = new StackPane(plain, hidden);
        field.setMinWidth(0);
        HBox.setHgrow(field, Priority.ALWAYS);
        hide.setMinWidth(Region.USE_PREF_SIZE);
        getChildren().addAll(field, hide);
    }

    public StringProperty textProperty() {
        return plain.valueProperty();
    }

    public String getText() {
        return plain.getText();
    }

    public void setText(String text) {
        String value = text == null ? "" : text;
        plain.setText(value);
        hide.setSelected(!value.isBlank() && !value.contains("${"));
    }
}
