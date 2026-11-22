package io.github.itech_mm.apiro.ui.settings;

import io.github.itech_framework.java_fx.loader.FxProvider;
import io.github.itech_framework.java_fx.ui.dialog.ModalDialog;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.project.ProjectSession;
import io.github.itech_mm.apiro.ui.component.AuthEditorView;
import java.util.List;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class ProjectSettingsDialog {

    private static final String STYLESHEET = "/static/css/settings.css";

    private static final String DIALOG_STYLESHEET = "/static/css/dialog.css";

    private ProjectSettingsDialog() {
    }

    public static void open(Window owner, ProjectSession session, FxProvider<AuthEditorView> authEditors) {
        Project project = session.project();
        if (project == null) {
            return;
        }
        ProjectDraft draft = new ProjectDraft(project);
        OverviewPage overview = new OverviewPage(draft, session.fileProperty().get());
        AuthorizationPage authorization = new AuthorizationPage(draft, authEditors.create());
        List<SettingsPage> pages = List.of(
                overview.page(),
                authorization.page(),
                page("Headers"),
                page("Variables"),
                page("Environments"),
                page("Request defaults"));
        ModalDialog.builder()
                .addOwner(owner instanceof Stage stage ? stage : null)
                .stylesheets(
                        ProjectSettingsDialog.class.getResource(DIALOG_STYLESHEET).toExternalForm(),
                        ProjectSettingsDialog.class.getResource(STYLESHEET).toExternalForm())
                .addContent(new SettingsShell(pages))
                .addActionButton("Save", () -> save(session, draft, pages))
                .addCloseButton("Cancel")
                .build()
                .show();
    }

    private static void save(ProjectSession session, ProjectDraft draft, List<SettingsPage> pages) {
        pages.forEach(page -> page.commit().run());
        session.updateProject(draft::applyTo);
        closeCurrent();
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