package io.github.itech_mm.apiro.ui.settings;

import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.Variable;
import io.github.itech_mm.apiro.ui.component.KeyValueTableView;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class VariablesPage {

    private final ProjectDraft draft;

    private final KeyValueTableView table;

    private final VBox root;

    public VariablesPage(ProjectDraft draft, KeyValueTableView table) {
        this.draft = draft;
        this.table = table;
        table.setItems(draft.getVariables().stream()
                .map(variable -> new KeyValue(variable.key(), variable.value(), variable.enabled()))
                .toList());
        Label hint = new Label("Available in every request, whatever environment is selected. They have the lowest "
                + "priority: a variable with the same name in a folder or in the selected environment wins. "
                + "If a name appears twice here, the first enabled row is used. Reference one as ${name}. "
                + "Values are saved in the project file as typed, so keep secrets in an environment variable "
                + "marked secret.");
        hint.setWrapText(true);
        hint.getStyleClass().add("settings-page-hint");
        ScrollPane scroll = new ScrollPane(table.getRoot());
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root = new VBox(12, hint, scroll);
    }

    public SettingsPage page() {
        return new SettingsPage("Variables", root, this::commit);
    }

    private void commit() {
        draft.setVariables(table.getItems().stream()
                .map(item -> new Variable(item.key().trim(), item.value(), item.enabled(), false))
                .toList());
    }
}