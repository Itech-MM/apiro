package io.github.itech_mm.apiro.ui.settings;

import io.github.itech_mm.apiro.model.Variable;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class VariableTable extends VBox {

    private final boolean allowSecret;

    private final VBox rowBox = new VBox(6);

    private final List<Row> rows = new ArrayList<>();

    public VariableTable(boolean allowSecret) {
        this.allowSecret = allowSecret;
        setSpacing(8);
        Button addButton = new Button("Add variable");
        addButton.getStyleClass().add("link-button");
        addButton.setOnAction(event -> add(Variable.of("", ""), true));
        getChildren().addAll(rowBox, addButton);
    }

    public void setItems(List<Variable> variables) {
        rows.clear();
        rowBox.getChildren().clear();
        variables.forEach(variable -> add(variable, false));
    }

    public List<Variable> getItems() {
        return rows.stream()
                .map(Row::toVariable)
                .filter(variable -> !variable.key().isBlank())
                .toList();
    }

    private void add(Variable variable, boolean focus) {
        Row row = new Row(variable);
        rows.add(row);
        rowBox.getChildren().add(row.box);
        if (focus) {
            Platform.runLater(row.key::requestFocus);
        }
    }

    private void remove(Row row) {
        rows.remove(row);
        rowBox.getChildren().remove(row.box);
    }

    private final class Row {

        private final HBox box = new HBox(8);

        private final CheckBox enabled = new CheckBox();

        private final TextField key = new TextField();

        private final TextField plain = new TextField();

        private final PasswordField hidden = new PasswordField();

        private final CheckBox secret = new CheckBox("Secret");

        Row(Variable variable) {
            box.setAlignment(Pos.CENTER_LEFT);
            box.getStyleClass().add("kv-row");
            enabled.setSelected(variable.enabled());
            key.setText(variable.key());
            key.setPromptText("name");
            key.setPrefWidth(140);
            key.setMinWidth(100);
            key.getStyleClass().add("kv-field");
            plain.setText(variable.value());
            plain.setPromptText("value");
            plain.getStyleClass().add("kv-field");
            hidden.setPromptText("value");
            hidden.getStyleClass().add("kv-field");
            hidden.textProperty().bindBidirectional(plain.textProperty());
            secret.setSelected(allowSecret && variable.secret());
            plain.visibleProperty().bind(secret.selectedProperty().not());
            plain.managedProperty().bind(plain.visibleProperty());
            hidden.visibleProperty().bind(secret.selectedProperty());
            hidden.managedProperty().bind(hidden.visibleProperty());
            StackPane value = new StackPane(plain, hidden);
            HBox.setHgrow(value, Priority.ALWAYS);
            value.setMinWidth(160);
            enabled.setMinWidth(Region.USE_PREF_SIZE);
            secret.setMinWidth(Region.USE_PREF_SIZE);
            Button removeButton = new Button();
            removeButton.getStyleClass().addAll("icon-button", "icon-button-sm");
            removeButton.setMinWidth(Region.USE_PREF_SIZE);
            Region icon = new Region();
            icon.getStyleClass().addAll("icon", "icon-sm", "icon-close");
            removeButton.setGraphic(icon);
            removeButton.setOnAction(event -> remove(this));
            enabled.selectedProperty().addListener((obs, was, now) -> markDisabled(!now));
            markDisabled(!enabled.isSelected());
            box.getChildren().addAll(enabled, key, value);
            if (allowSecret) {
                box.getChildren().add(secret);
            }
            box.getChildren().add(removeButton);
        }

        private void markDisabled(boolean disabled) {
            if (disabled) {
                if (!box.getStyleClass().contains("kv-disabled")) {
                    box.getStyleClass().add("kv-disabled");
                }
            } else {
                box.getStyleClass().remove("kv-disabled");
            }
        }

        private Variable toVariable() {
            return new Variable(key.getText().trim(), plain.getText(), enabled.isSelected(),
                    allowSecret && secret.isSelected());
        }
    }
}