package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.ui.editor.VariableField;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class HeaderEditor extends VBox {

    private final VBox rows = new VBox(4);
    private final List<Row> list = new ArrayList<>();
    private final Consumer<List<KeyValue>> onChange;
    private boolean loading;

    HeaderEditor(Consumer<List<KeyValue>> onChange) {
        super(8);
        this.onChange = onChange;
        Label title = new Label("Headers");
        title.getStyleClass().add("section-title");
        Label hint = new Label("Added to this step only. A header with the same name replaces the request's own.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        getStyleClass().add("section-body");
        Button add = new Button("Add header");
        add.getStyleClass().add("link-button");
        add.setOnAction(event -> {
            addRow(KeyValue.of("", ""));
            emit();
        });
        getChildren().addAll(title, hint, rows, add);
    }

    void set(List<KeyValue> headers) {
        loading = true;
        rows.getChildren().clear();
        list.clear();
        headers.forEach(this::addRow);
        loading = false;
    }

    private void addRow(KeyValue header) {
        Row row = new Row(header);
        list.add(row);
        rows.getChildren().add(row.box);
    }

    private void emit() {
        if (!loading) {
            onChange.accept(list.stream().map(Row::value).toList());
        }
    }

    private final class Row {

        final CheckBox enabled = new CheckBox();
        final VariableField key = new VariableField();
        final VariableField value = new VariableField();
        final Button remove = new Button("×");
        final HBox box = new HBox(4, enabled, key, value, remove);

        Row(KeyValue header) {
            key.getStyleClass().add("kv-field");
            value.getStyleClass().add("kv-field");
            remove.getStyleClass().addAll("icon-button", "icon-button-sm");
            key.setMinWidth(170);
            key.setPrefWidth(170);
            key.setMaxWidth(170);
            HBox.setHgrow(value, Priority.ALWAYS);
            enabled.setSelected(header.enabled());
            key.setText(header.key());
            value.setText(header.value());
            box.setAlignment(Pos.CENTER_LEFT);
            enabled.selectedProperty().addListener((obs, oldValue, newValue) -> emit());
            key.valueProperty().addListener((obs, oldValue, newValue) -> emit());
            value.valueProperty().addListener((obs, oldValue, newValue) -> emit());
            remove.setOnAction(event -> {
                list.remove(this);
                rows.getChildren().remove(box);
                emit();
            });
        }

        KeyValue value() {
            return new KeyValue(key.getText(), value.getText(), enabled.isSelected());
        }
    }
}
