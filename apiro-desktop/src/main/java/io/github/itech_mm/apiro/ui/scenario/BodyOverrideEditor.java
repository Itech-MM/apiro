package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_framework.code_editor.CodeEditor;
import io.github.itech_mm.apiro.model.BodyOverride;
import io.github.itech_mm.apiro.model.FieldOverride;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.service.http.BodyOverrides;
import io.github.itech_mm.apiro.ui.EditorStyle;
import io.github.itech_mm.apiro.ui.JsonPretty;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

final class BodyOverrideEditor extends VBox {

    private static final String USE = "Use request body";
    private static final String PATCH = "Patch fields";
    private static final String REPLACE = "Replace body";

    private final Consumer<BodyOverride> onChange;
    private final ComboBox<String> mode = new ComboBox<>(FXCollections.observableArrayList(USE, PATCH, REPLACE));
    private final VBox rows = new VBox(6);
    private final List<Row> list = new ArrayList<>();
    private final VBox patchBox = new VBox(8);
    private final VBox replaceBox = new VBox(8);
    private final CodeEditor replaceEditor = new CodeEditor();
    private final VBox previewBox = new VBox(6);
    private final CodeEditor preview = new CodeEditor();
    private final Label error = new Label();
    private RequestItem request;
    private boolean loading;

    BodyOverrideEditor(Consumer<BodyOverride> onChange) {
        super(8);
        this.onChange = onChange;
        getStyleClass().add("section-body");
        Label title = new Label("Request body");
        title.getStyleClass().add("section-title");
        mode.getStyleClass().add("field-combo");
        mode.setMaxWidth(Double.MAX_VALUE);
        mode.setValue(USE);

        Button add = new Button("Add field");
        add.getStyleClass().add("link-button");
        add.setOnAction(event -> {
            addRow(FieldOverride.of("", FieldOverride.ValueType.STRING, ""));
            emit();
        });
        patchBox.getChildren().addAll(rows, add);

        EditorStyle.applyInset(replaceEditor);
        replaceEditor.getStyleClass().add("override-editor");
        replaceEditor.setLanguage("json");
        replaceEditor.setShowLineNumbers(false);
        replaceEditor.setMinHeight(170);
        replaceEditor.setPrefHeight(170);
        replaceEditor.textProperty().addListener((obs, oldValue, newValue) -> emit());
        Button copy = new Button("Copy from request");
        copy.getStyleClass().add("link-button");
        copy.setOnAction(event -> copyFromRequest());
        replaceBox.getChildren().addAll(replaceEditor, copy);

        Label previewTitle = new Label("Effective body");
        previewTitle.getStyleClass().add("muted");
        EditorStyle.applyInset(preview);
        preview.getStyleClass().add("override-editor");
        preview.setLanguage("json");
        preview.setEditable(false);
        preview.setShowLineNumbers(false);
        preview.setMinHeight(150);
        preview.setPrefHeight(150);
        error.getStyleClass().add("override-error");
        error.setWrapText(true);
        previewBox.getChildren().addAll(previewTitle, preview, error);

        getChildren().addAll(title, mode, patchBox, replaceBox, previewBox);
        mode.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!loading && REPLACE.equals(newValue) && replaceEditor.getText().isBlank()) {
                copyFromRequest();
            }
            layoutForMode();
            emit();
        });
        layoutForMode();
    }

    private void copyFromRequest() {
        if (request != null) {
            String text = request.body().text();
            replaceEditor.setText(JsonPretty.format(text).orElse(text));
        }
    }

    void set(BodyOverride override, RequestItem owner) {
        loading = true;
        request = owner;
        rows.getChildren().clear();
        list.clear();
        if (override == null) {
            mode.setValue(USE);
            replaceEditor.setText("");
        } else {
            override.fields().forEach(this::addRow);
            replaceEditor.setText(override.text());
            mode.setValue(override.mode() == BodyOverride.Mode.REPLACE ? REPLACE : PATCH);
        }
        loading = false;
        layoutForMode();
        updatePreview();
    }

    private void layoutForMode() {
        String selected = mode.getValue();
        show(patchBox, PATCH.equals(selected));
        show(replaceBox, REPLACE.equals(selected));
        show(previewBox, PATCH.equals(selected));
    }

    private static void show(VBox box, boolean visible) {
        box.setVisible(visible);
        box.setManaged(visible);
    }

    private void addRow(FieldOverride field) {
        Row row = new Row(field);
        list.add(row);
        rows.getChildren().add(row.box);
    }

    private BodyOverride value() {
        String selected = mode.getValue();
        if (USE.equals(selected)) {
            return null;
        }
        List<FieldOverride> fields = list.stream().map(Row::value).toList();
        return new BodyOverride(REPLACE.equals(selected) ? BodyOverride.Mode.REPLACE : BodyOverride.Mode.PATCH,
                replaceEditor.getText(), fields);
    }

    private void emit() {
        if (loading) {
            return;
        }
        updatePreview();
        onChange.accept(value());
    }

    private void updatePreview() {
        if (!PATCH.equals(mode.getValue())) {
            return;
        }
        if (request == null) {
            preview.setText("");
            error.setText("The request for this step is missing");
            return;
        }
        try {
            String text = BodyOverrides.apply(request, value()).body().text();
            preview.setText(text);
            error.setText("");
        } catch (IllegalArgumentException e) {
            preview.setText("");
            error.setText(e.getMessage());
        }
    }

    private final class Row {

        final CheckBox enabled = new CheckBox();
        final TextField path = new TextField();
        final Button remove = new Button("×");
        final ComboBox<FieldOverride.ValueType> type = new ComboBox<>(
                FXCollections.observableArrayList(FieldOverride.ValueType.values()));
        final TextField value = new TextField();
        final VBox box;

        Row(FieldOverride field) {
            enabled.setSelected(field.enabled());
            path.getStyleClass().add("kv-field");
            path.setPromptText("$.user.name");
            HBox.setHgrow(path, Priority.ALWAYS);
            remove.getStyleClass().addAll("icon-button", "icon-button-sm");
            HBox first = new HBox(6, enabled, path, remove);
            first.setAlignment(Pos.CENTER_LEFT);
            type.getStyleClass().add("field-combo");
            type.setPrefWidth(100);
            type.setMinWidth(Region.USE_PREF_SIZE);
            type.setConverter(new StringConverter<>() {
                @Override
                public String toString(FieldOverride.ValueType item) {
                    return item == null ? "" : item.name().toLowerCase();
                }

                @Override
                public FieldOverride.ValueType fromString(String text) {
                    return FieldOverride.ValueType.valueOf(text.toUpperCase());
                }
            });
            value.getStyleClass().add("kv-field");
            value.setPromptText("value");
            HBox.setHgrow(value, Priority.ALWAYS);
            HBox second = new HBox(6, type, value);
            second.setAlignment(Pos.CENTER_LEFT);
            box = new VBox(4, first, second);
            box.getStyleClass().add("override-row");
            path.setText(field.path());
            type.setValue(field.type());
            value.setText(field.value());
            syncValue();
            enabled.selectedProperty().addListener((obs, oldValue, newValue) -> emit());
            path.textProperty().addListener((obs, oldValue, newValue) -> emit());
            value.textProperty().addListener((obs, oldValue, newValue) -> emit());
            type.valueProperty().addListener((obs, oldValue, newValue) -> {
                syncValue();
                emit();
            });
            remove.setOnAction(event -> {
                list.remove(this);
                rows.getChildren().remove(box);
                emit();
            });
        }

        private void syncValue() {
            FieldOverride.ValueType selected = type.getValue();
            boolean none = selected == FieldOverride.ValueType.NULL || selected == FieldOverride.ValueType.REMOVE;
            value.setDisable(none);
            value.setPromptText(selected == FieldOverride.ValueType.JSON ? "{\"id\": 1}" : "value");
        }

        FieldOverride value() {
            return new FieldOverride(path.getText(), type.getValue(), value.getText(), enabled.isSelected());
        }
    }
}
