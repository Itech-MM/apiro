package io.github.itech_mm.apiro.ui.settings;

import io.github.itech_framework.java_fx.loader.FxProvider;
import io.github.itech_framework.java_fx.ui.dialog.AlertDialog;
import io.github.itech_framework.java_fx.ui.dialog.ModalDialog;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.project.ProjectSession;
import io.github.itech_mm.apiro.ui.component.AuthEditorView;
import io.github.itech_mm.apiro.ui.component.KeyValueTableView;
import java.io.IOException;
import java.util.List;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class ProjectSettingsDialog {

    private static final String APP_STYLESHEET = "/static/css/app.css";

    private static final String STYLESHEET = "/static/css/settings.css";

    private static final String DIALOG_STYLESHEET = "/static/css/dialog.css";

    private ProjectSettingsDialog() {
    }

    public static void open(Window owner, ProjectSession session, FxProvider<AuthEditorView> authEditors,
                            FxProvider<KeyValueTableView> tables) {
        Project project = session.project();
        if (project == null) {
            return;
        }
        ProjectDraft draft = new ProjectDraft(project);
        OverviewPage overview = new OverviewPage(draft, session.fileProperty().get());
        AuthorizationPage authorization = new AuthorizationPage(draft, authEditors.create());
        HeadersPage headers = new HeadersPage(draft, tables.create());
        VariablesPage variables = new VariablesPage(draft, tables.create());
        EnvironmentsPage environments = new EnvironmentsPage(draft);
        List<SettingsPage> pages = List.of(
                overview.page(),
                authorization.page(),
                headers.page(),
                variables.page(),
                environments.page(),
                page("Request defaults"));
        ModalDialog.builder()
                .addOwner(owner instanceof Stage stage ? stage : null)
                .stylesheets(
                        ProjectSettingsDialog.class.getResource(APP_STYLESHEET).toExternalForm(),
                        ProjectSettingsDialog.class.getResource(DIALOG_STYLESHEET).toExternalForm(),
                        ProjectSettingsDialog.class.getResource(STYLESHEET).toExternalForm())
                .addContent(new SettingsShell(pages))
                .addActionButton("Save", () -> save(owner, session, draft, pages))
                .addCloseButton("Cancel")
                .build()
                .show();
    }

    private static void save(Window owner, ProjectSession session, ProjectDraft draft, List<SettingsPage> pages) {
        pages.forEach(page -> page.commit().run());
        session.updateProject(draft::applyTo);
        try {
            session.save();
        } catch (IOException e) {
            closeCurrent();
            showSaveError(owner, e);
            return;
        }
        closeCurrent();
    }

    private static void showSaveError(Window owner, IOException error) {
        String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        AlertDialog.builder()
                .level(AlertDialog.Level.ERROR)
                .title("Settings applied, file not saved")
                .message("The settings are applied in the open project, but writing the file failed: " + detail
                        + ". Press Ctrl+S to try again.")
                .addOwner(owner instanceof Stage stage ? stage : null)
                .addButton("OK", null)
                .build()
                .showAsync();
    }

    private static SettingsPage page(String title) {
        Label heading = new Label(title);
        heading.getStyleClass().add("settings-page-title");
        Label hint = new Label("Coming in the next steps");
        hint.getStyleClass().add("settings-page-hint");
        return new SettingsPage(title, new VBox(6, heading, hint));
    }

    private static void closeCurrent() {
        ModalDialog dialog = ModalDialog.getCurrentDialog();
        if (dialog != null) {
            dialog.close();
        }
    }
}