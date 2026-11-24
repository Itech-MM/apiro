package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_mm.apiro.model.Assertion;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class AssertionEditor extends VBox {

    private final VBox rows = new VBox(8);
    private final List<Row> list = new ArrayList<>();
    private final Consumer<List<Assertion>> onChange;
    private boolean loading;

    AssertionEditor(Consumer<List<Assertion>> onChange) {
        super(8);
        this.onChange = onChange;
        Label title = new Label("Assertions");
        title.getStyleClass().add("section-title");
        getStyleClass().add("section-body");
        Button add = new Button("Add assertion");
        add.getStyleClass().add("link-button");
        add.setOnAction(event -> {
            addRow(new Assertion(Assertion.Source.STATUS, "", Assertion.Operator.EQUALS, "200"));
            emit();
        });
        getChildren().addAll(title, rows, add);
    }

    void set(List<Assertion> assertions) {
        loading = true;
        rows.getChildren().clear();
        list.clear();
        assertions.forEach(this::addRow);
        loading = false;
    }

    private void addRow(Assertion assertion) {
        Row row = new Row(assertion);
        list.add(row);
        rows.getChildren().add(row.box);
    }

    private void emit() {
        if (!loading) {
            onChange.accept(list.stream().map(Row::value).toList());
        }
    }

    private final class Row {

        final ComboBox<Assertion.Source> source = new ComboBox<>(
                FXCollections.observableArrayList(Assertion.Source.values()));
        final TextField expression = new TextField();
        final ComboBox<Assertion.Operator> operator = new ComboBox<>(
                FXCollections.observableArrayList(Assertion.Operator.values()));
        final TextField expected = new TextField();
        final Button remove = new Button("×");
        final VBox box;

        Row(Assertion assertion) {
            source.setValue(assertion.source());
            expression.setText(assertion.expression());
            operator.setValue(assertion.operator());
            expected.setText(assertion.expected());
            expression.getStyleClass().add("kv-field");
            expected.getStyleClass().add("kv-field");
            remove.getStyleClass().addAll("icon-button", "icon-button-sm");
            expression.setPromptText("path or header name");
            expected.setPromptText("expected");
            HBox.setHgrow(expression, Priority.ALWAYS);
            HBox.setHgrow(expected, Priority.ALWAYS);
            HBox first = new HBox(4, source, expression, remove);
            HBox second = new HBox(4, operator, expected);
            first.setAlignment(Pos.CENTER_LEFT);
            second.setAlignment(Pos.CENTER_LEFT);
            box = new VBox(4, first, second);
            box.getStyleClass().add("assertion-row");
            syncEnabled();
            source.valueProperty().addListener((obs, oldValue, newValue) -> {
                syncEnabled();
                emit();
            });
            operator.valueProperty().addListener((obs, oldValue, newValue) -> {
                syncEnabled();
                emit();
            });
            expression.textProperty().addListener((obs, oldValue, newValue) -> emit());
            expected.textProperty().addListener((obs, oldValue, newValue) -> emit());
            remove.setOnAction(event -> {
                list.remove(this);
                rows.getChildren().remove(box);
                emit();
            });
        }

        private void syncEnabled() {
            Assertion.Source current = source.getValue();
            expression.setDisable(current != Assertion.Source.JSON_PATH && current != Assertion.Source.HEADER);
            Assertion.Operator op = operator.getValue();
            expected.setDisable(op == Assertion.Operator.EXISTS || op == Assertion.Operator.NOT_EXISTS);
        }

        Assertion value() {
            return new Assertion(source.getValue(), expression.getText(), operator.getValue(), expected.getText());
        }
    }
}
