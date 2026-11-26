package io.github.itech_mm.apiro.ui.settings;

import io.github.itech_mm.apiro.ui.component.KeyValueTableView;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class HeadersPage {

    private final ProjectDraft draft;

    private final KeyValueTableView table;

    private final VBox root;

    public HeadersPage(ProjectDraft draft, KeyValueTableView table) {
        this.draft = draft;
        this.table = table;
        table.setItems(draft.getHeaders());
        Label hint = new Label("Sent with every request in this project. A folder or request can replace a header "
                + "with the same name, or switch it off by adding that name as a disabled row.");
        hint.setWrapText(true);
        hint.getStyleClass().add("settings-page-hint");
        ScrollPane scroll = new ScrollPane(table.getRoot());
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root = new VBox(12, hint, scroll);
    }

    public SettingsPage page() {
        return new SettingsPage("Headers", root, this::commit);
    }

    private void commit() {
        draft.setHeaders(table.getItems());
    }
}