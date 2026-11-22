package io.github.itech_mm.apiro.ui.settings;

import io.github.itech_mm.apiro.model.Environment;
import io.github.itech_mm.apiro.model.Variable;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class EnvironmentsPage {

    private final ProjectDraft draft;

    private final ObservableList<Entry> entries = FXCollections.observableArrayList();

    private final ListView<Entry> list = new ListView<>(entries);

    private final TextField nameField = new TextField();

    private final VariableTable table = new VariableTable(true);

    private final VBox editor;

    private final HBox root;

    private Entry current;

    private boolean loading;

    public EnvironmentsPage(ProjectDraft draft) {
        this.draft = draft;
        draft.getEnvironments().forEach(environment ->
                entries.add(new Entry(environment.id(), environment.name(), environment.variables())));

        list.getStyleClass().add("settings-env-list");
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, next) -> select(next));

        Button addButton = new Button("Add");
        addButton.getStyleClass().add("link-button");
        addButton.setOnAction(event -> addEnvironment());
        Button duplicateButton = new Button("Duplicate");
        duplicateButton.getStyleClass().add("link-button");
        duplicateButton.setOnAction(event -> duplicateEnvironment());
        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("link-button");
        deleteButton.setOnAction(event -> deleteEnvironment());
        duplicateButton.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        VBox side = new VBox(8, list, new HBox(2, addButton, duplicateButton, deleteButton));
        side.setPrefWidth(190);
        side.setMinWidth(190);
        VBox.setVgrow(list, Priority.ALWAYS);

        Label nameLabel = new Label("Name");
        nameLabel.getStyleClass().add("settings-field-label");
        nameField.textProperty().addListener((obs, old, text) -> {
            if (!loading && current != null) {
                current.name = text;
                list.refresh();
            }
        });
        Label hint = new Label("Secret values are not written to the project file. They are stored in the .apiro "
                + "folder next to it, so keep that folder out of version control.");
        hint.setWrapText(true);
        hint.getStyleClass().add("settings-page-hint");
        ScrollPane scroll = new ScrollPane(table);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        editor = new VBox(8, nameLabel, nameField, hint, scroll);
        HBox.setHgrow(editor, Priority.ALWAYS);

        root = new HBox(16, side, editor);
        if (entries.isEmpty()) {
            editor.setDisable(true);
        } else {
            list.getSelectionModel().select(0);
        }
    }

    public SettingsPage page() {
        return new SettingsPage("Environments", root, this::commit);
    }

    private void select(Entry next) {
        store();
        current = next;
        loading = true;
        editor.setDisable(next == null);
        nameField.setText(next == null ? "" : next.name);
        table.setItems(next == null ? List.of() : next.variables);
        loading = false;
    }

    private void store() {
        if (current != null) {
            current.variables = table.getItems();
        }
    }

    private void addEnvironment() {
        Environment created = Environment.of("New environment", List.of());
        Entry entry = new Entry(created.id(), created.name(), created.variables());
        entries.add(entry);
        list.getSelectionModel().select(entry);
        nameField.requestFocus();
        nameField.selectAll();
    }

    private void duplicateEnvironment() {
        if (current == null) {
            return;
        }
        store();
        Environment copy = Environment.of(current.name + " copy", current.variables);
        Entry entry = new Entry(copy.id(), copy.name(), copy.variables());
        entries.add(entries.indexOf(current) + 1, entry);
        list.getSelectionModel().select(entry);
    }

    private void deleteEnvironment() {
        Entry target = current;
        if (target == null) {
            return;
        }
        int index = entries.indexOf(target);
        current = null;
        entries.remove(target);
        if (entries.isEmpty()) {
            select(null);
        } else {
            list.getSelectionModel().select(Math.min(index, entries.size() - 1));
        }
    }

    private void commit() {
        store();
        draft.setEnvironments(entries.stream()
                .map(entry -> new Environment(entry.id, entry.name.isBlank() ? "Environment" : entry.name.trim(),
                        entry.variables))
                .toList());
    }

    private static final class Entry {

        private final String id;

        private String name;

        private List<Variable> variables;

        private Entry(String id, String name, List<Variable> variables) {
            this.id = id;
            this.name = name;
            this.variables = variables;
        }

        @Override
        public String toString() {
            return name.isBlank() ? "(unnamed)" : name;
        }
    }
}