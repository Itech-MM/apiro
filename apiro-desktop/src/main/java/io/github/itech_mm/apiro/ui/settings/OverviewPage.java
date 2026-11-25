package io.github.itech_mm.apiro.ui.settings;

import java.nio.file.Path;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public final class OverviewPage {

    private final ProjectDraft draft;

    private final TextField nameField = new TextField();

    private final TextArea descriptionArea = new TextArea();

    private final VBox root;

    public OverviewPage(ProjectDraft draft, Path file) {
        this.draft = draft;
        nameField.setText(draft.getName());
        descriptionArea.setText(draft.getDescription());
        descriptionArea.setPrefRowCount(5);
        descriptionArea.setWrapText(true);
        TextField fileField = new TextField(file == null ? "" : file.toString());
        fileField.setEditable(false);
        root = new VBox(14,
                group("Name", nameField),
                group("Description", descriptionArea),
                group("Project file", fileField));
    }

    public SettingsPage page() {
        return new SettingsPage("Overview", root, this::commit);
    }

    private void commit() {
        draft.setName(nameField.getText());
        draft.setDescription(descriptionArea.getText());
    }

    private static VBox group(String title, Node field) {
        Label label = new Label(title);
        label.getStyleClass().add("settings-field-label");
        return new VBox(4, label, field);
    }
}