package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.ui.editor.VariableField;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class QueryEditor extends VBox {

    private final VBox rows = new VBox(4);
    private final List<Row> list = new ArrayList<>();
    private final Consumer<List<KeyValue>> onChange;
    private final Button copyButton = new Button("Copy from request");
    private RequestItem request;
    private boolean loading;

    QueryEditor(Consumer<List<KeyValue>> onChange) {
        super(8);
        this.onChange = onChange;
        HBox header = InfoTip.header("Query parameters",
                "Added to this step only. A parameter with the same name replaces the request's own.");
        getStyleClass().add("section-body");
        Button add = new Button("Add parameter");
        add.getStyleClass().add("link-button");
        add.setOnAction(event -> {
            addRow(KeyValue.of("", ""));
            emit();
        });
        copyButton.getStyleClass().add("link-button");
        copyButton.setOnAction(event -> copyFromRequest());
        copyButton.setVisible(false);
        copyButton.setManaged(false);
        HBox actions = new HBox(10, add, copyButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(header, rows, actions);
    }

    void set(List<KeyValue> query, RequestItem owner) {
        loading = true;
        request = owner;
        rows.getChildren().clear();
        list.clear();
        if (query != null) {
            query.forEach(this::addRow);
        }
        boolean hasRequestQuery = owner != null && !owner.query().isEmpty();
        copyButton.setVisible(hasRequestQuery);
        copyButton.setManaged(hasRequestQuery);
        loading = false;
    }

    private void copyFromRequest() {
        if (request == null || request.query().isEmpty()) {
            return;
        }
        loading = true;
        rows.getChildren().clear();
        list.clear();
        for (KeyValue entry : request.query()) {
            addRow(new KeyValue(entry.key(), entry.value(), entry.enabled()));
        }
        loading = false;
        emit();
    }

    private void addRow(KeyValue entry) {
        Row row = new Row(entry);
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

        Row(KeyValue entry) {
            key.getStyleClass().add("kv-field");
            value.getStyleClass().add("kv-field");
            remove.getStyleClass().addAll("icon-button", "icon-button-sm");
            key.setMinWidth(170);
            key.setPrefWidth(170);
            key.setMaxWidth(170);
            HBox.setHgrow(value, Priority.ALWAYS);
            enabled.setSelected(entry.enabled());
            key.setText(entry.key());
            value.setText(entry.value());
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
