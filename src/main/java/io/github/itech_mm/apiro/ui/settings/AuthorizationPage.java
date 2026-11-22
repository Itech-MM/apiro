package io.github.itech_mm.apiro.ui.settings;

import io.github.itech_mm.apiro.ui.component.AuthEditorView;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class AuthorizationPage {

    private final ProjectDraft draft;

    private final AuthEditorView editor;

    private final VBox root;

    public AuthorizationPage(ProjectDraft draft, AuthEditorView editor) {
        this.draft = draft;
        this.editor = editor;
        editor.setAllowInherit(false);
        editor.setConfig(draft.getAuth());
        Label hint = new Label("Used by every folder and request set to Inherit. Values are saved in the project "
                + "file as typed, so use a variable such as {{token}} for secrets.");
        hint.setWrapText(true);
        hint.getStyleClass().add("settings-page-hint");
        root = new VBox(14, hint, editor.getRoot());
    }

    public SettingsPage page() {
        return new SettingsPage("Authorization", root, this::commit);
    }

    private void commit() {
        draft.setAuth(editor.getConfig());
    }
}