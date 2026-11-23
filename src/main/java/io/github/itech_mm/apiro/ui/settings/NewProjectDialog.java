package io.github.itech_mm.apiro.ui.settings;

import io.github.itech_framework.java_fx.loader.FxProvider;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.ui.component.AuthEditorView;
import io.github.itech_mm.apiro.ui.component.KeyValueTableView;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javafx.stage.Stage;
import javafx.stage.Window;

/** New project dialog: file location plus the same project settings pages as the settings dialog. */
public final class NewProjectDialog {

	/** Writes the new project file and opens it. */
	@FunctionalInterface
	public interface Creator {

		void create(Path file, Project project) throws IOException;
	}

	private static final String DEFAULT_NAME = "New Project";

	private static final String APP_STYLESHEET = "/static/css/app.css";

	private static final String STYLESHEET = "/static/css/settings.css";

	private static final String DIALOG_STYLESHEET = "/static/css/dialog.css";

	private NewProjectDialog() {
	}

	public static void open(Window owner, Path defaultLocation, FxProvider<AuthEditorView> authEditors,
			FxProvider<KeyValueTableView> tables, Creator creator) {
		ProjectDraft draft = new ProjectDraft(Project.create(DEFAULT_NAME));
		NewProjectGeneralPage general = new NewProjectGeneralPage(draft, owner, defaultLocation, DEFAULT_NAME);
		SettingsPage generalPage = general.page();
		List<SettingsPage> pages = List.of(generalPage,
				new AuthorizationPage(draft, authEditors.create()).page(),
				new HeadersPage(draft, tables.create()).page(),
				new VariablesPage(draft, tables.create()).page(), new EnvironmentsPage(draft).page(),
				new RequestDefaultsPage(draft).page());
		SettingsShell shell = new SettingsShell("New Project", "", pages);
		SettingsModal.create(owner instanceof Stage stage ? stage : null,
				List.of(NewProjectDialog.class.getResource(APP_STYLESHEET).toExternalForm(),
						NewProjectDialog.class.getResource(DIALOG_STYLESHEET).toExternalForm(),
						NewProjectDialog.class.getResource(STYLESHEET).toExternalForm()),
				shell, "Create", () -> create(general, generalPage, draft, pages, shell, creator), "Cancel").show();
	}

	private static void create(NewProjectGeneralPage general, SettingsPage generalPage, ProjectDraft draft,
			List<SettingsPage> pages, SettingsShell shell, Creator creator) {
		for (SettingsPage page : pages) {
			if (!page.problem().get().isEmpty()) {
				shell.select(page);
				return;
			}
		}
		pages.forEach(page -> page.commit().run());
		Project project = draft.applyTo(Project.create(draft.getName()));
		try {
			creator.create(general.targetFile(), project);
		} catch (IOException e) {
			general.showError("Could not create the project: "
					+ (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
			shell.select(generalPage);
			return;
		}
		SettingsModal dialog = SettingsModal.current();
		if (dialog != null) {
			dialog.close();
		}
	}
}
