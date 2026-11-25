package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_mm.apiro.model.Extractor;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

final class ExtractorEditor extends VBox {

    private final VBox rows = new VBox(4);
    private final List<Row> list = new ArrayList<>();
    private final Consumer<List<Extractor>> onChange;
    private boolean loading;

    ExtractorEditor(Consumer<List<Extractor>> onChange) {
        super(8);
        this.onChange = onChange;
        Label title = new Label("Extract values");
        title.getStyleClass().add("section-title");
        getStyleClass().add("section-body");
        Button add = new Button("Add extractor");
        add.getStyleClass().add("link-button");
        add.setOnAction(event -> {
            addRow(new Extractor("", Extractor.Source.JSON_PATH, ""));
            emit();
        });
        getChildren().addAll(title, rows, add);
    }

    void set(List<Extractor> extractors) {
        loading = true;
        rows.getChildren().clear();
        list.clear();
        extractors.forEach(this::addRow);
        loading = false;
    }

    private void addRow(Extractor extractor) {
        Row row = new Row(extractor);
        list.add(row);
        rows.getChildren().add(row.box);
    }

    private void emit() {
        if (!loading) {
            onChange.accept(list.stream().map(Row::value).toList());
        }
    }

    private final class Row {

        final TextField variable = new TextField();
        final ComboBox<Extractor.Source> source = new ComboBox<>(
                FXCollections.observableArrayList(Extractor.Source.values()));
        final TextField expression = new TextField();
        final Button remove = new Button("×");
        final HBox box = new HBox(4, variable, source, expression, remove);

        Row(Extractor extractor) {
            variable.getStyleClass().add("kv-field");
            source.getStyleClass().add("field-combo");
            source.setPrefWidth(130);
            source.setMinWidth(Region.USE_PREF_SIZE);
            expression.getStyleClass().add("kv-field");
            remove.getStyleClass().addAll("icon-button", "icon-button-sm");
            variable.setPromptText("variable");
            variable.setPrefWidth(100);
            expression.setPromptText("$.data.token");
            HBox.setHgrow(expression, Priority.ALWAYS);
            variable.setText(extractor.variable());
            source.setValue(extractor.source());
            expression.setText(extractor.expression());
            box.setAlignment(Pos.CENTER_LEFT);
            variable.textProperty().addListener((obs, oldValue, newValue) -> emit());
            expression.textProperty().addListener((obs, oldValue, newValue) -> emit());
            source.valueProperty().addListener((obs, oldValue, newValue) -> emit());
            remove.setOnAction(event -> {
                list.remove(this);
                rows.getChildren().remove(box);
                emit();
            });
        }

        Extractor value() {
            return new Extractor(variable.getText(), source.getValue(), expression.getText());
        }
    }
}
