package io.github.itech_mm.apiro.ui.component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github.itech_framework.code_editor.CodeEditor;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.FxUse;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import io.github.itech_framework.java_fx.loader.FxProvider;
import io.github.itech_mm.apiro.model.BodyType;
import io.github.itech_mm.apiro.model.RequestBody;
import io.github.itech_mm.apiro.ui.MonoFont;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

@FxComponent("/views/components/body-editor.fxml")
public class BodyEditorView {

    @FXML
    @Root
    private VBox root;

    @FXML
    private HBox chipBox;

    @FXML
    private Label contentTypeLabel;

    @FXML
    private StackPane contentPane;

    @FXML
    private CodeEditor editor;

    @FXML
    private Label placeholderLabel;

    @FxUse
    private FxProvider<KeyValueTableView> formTables;

    private final ToggleGroup group = new ToggleGroup();
    private final Map<BodyType, ToggleButton> chips = new EnumMap<>(BodyType.class);
    private final ReadOnlyStringWrapper preview = new ReadOnlyStringWrapper("none");
    private KeyValueTableView formTable;

    @OnInit
    private void init() {
        formTable = formTables.get();
        contentPane.getChildren().add(formTable.getRoot());
        editor.setTheme("light-plus");
        editor.setFontSize(MonoFont.EDITOR_SIZE);

        for (BodyType type : BodyType.values()) {
            ToggleButton chip = new ToggleButton(label(type));
            chip.getStyleClass().add("chip");
            chip.setToggleGroup(group);
            chip.setUserData(type);
            chips.put(type, chip);
            chipBox.getChildren().add(chip);
        }
        group.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> onSelectionChanged(oldToggle, newToggle));
        group.selectToggle(chips.get(BodyType.NONE));
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

    public void setBody(RequestBody body) {
        editor.setText(body.text());
        formTable.setItems(body.form());
        group.selectToggle(chips.get(body.type()));
    }

    public RequestBody getBody() {
        return new RequestBody(selectedType(), editor.getText(), "", "UTF-8", formTable.getItems(), List.of(), "");
    }

    private void onSelectionChanged(Toggle oldToggle, Toggle newToggle) {
        if (newToggle == null) {
            group.selectToggle(oldToggle);
            return;
        }
        render();
    }

    private BodyType selectedType() {
        Toggle selected = group.getSelectedToggle();
        return selected == null ? BodyType.NONE : (BodyType) selected.getUserData();
    }

    private void render() {
        BodyType type = selectedType();
        boolean text = type.isText();
        boolean form = type == BodyType.FORM;

        show(editor, text);
        show(formTable.getRoot(), form);
        show(placeholderLabel, !text && !form);
        placeholderLabel.setText(switch (type) {
            case NONE -> "This request has no body";
            case MULTIPART, BINARY -> "Not available yet";
            default -> "";
        });

        if (text) {
            editor.setLanguage(type == BodyType.JSON ? "json" : "plaintext");
        }
        contentTypeLabel.setText(type.defaultContentType());
        preview.set(label(type));
    }

    private void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static String label(BodyType type) {
        return switch (type) {
            case NONE -> "none";
            case JSON -> "JSON";
            case XML -> "XML";
            case TEXT -> "Text";
            case HTML -> "HTML";
            case FORM -> "Form";
            case MULTIPART -> "Multipart";
            case BINARY -> "Binary";
        };
    }
}