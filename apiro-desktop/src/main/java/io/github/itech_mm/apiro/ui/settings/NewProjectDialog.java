package io.github.itech_mm.apiro.ui.settings;

import io.github.itech_framework.java_fx.loader.FxProvider;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.service.manifest.ManifestImporter;
import io.github.itech_mm.apiro.service.manifest.ManifestReimport;
import io.github.itech_mm.apiro.ui.component.AuthEditorView;
import io.github.itech_mm.apiro.ui.component.KeyValueTableView;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javafx.stage.Stage;
import javafx.stage.Window;

/** New project dialog: file location, an optional manifest import and the project settings pages. */
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
		ManifestReviewPage review = new ManifestReviewPage();
		SettingsPage reviewPage = new SettingsPage("Review", review.page().content());
		AtomicReference<ManifestSourcePage.Loaded> loaded = new AtomicReference<>();
		AtomicReference<SettingsShell> shellRef = new AtomicReference<>();
		Project blank = Project.create(DEFAULT_NAME);
		ManifestSourcePage source = new ManifestSourcePage(owner, () -> blank, data -> {
			loaded.set(data);
			review.show(data.diff(), Set.of());
			shellRef.get().select(reviewPage);
		});
		SettingsPage manifestPage = new SettingsPage("Manifest", source.page().content());
		List<SettingsPage> pages = List.of(generalPage, manifestPage, reviewPage,
				new AuthorizationPage(draft, authEditors.create()).page(),
				new HeadersPage(draft, tables.create()).page(),
				new VariablesPage(draft, tables.create()).page(), new EnvironmentsPage(draft).page(),
				new RequestDefaultsPage(draft).page());
		SettingsShell shell = new SettingsShell("New Project", "", pages);
		shellRef.set(shell);
		SettingsModal.create(owner instanceof Stage stage ? stage : null,
				List.of(NewProjectDialog.class.getResource(APP_STYLESHEET).toExternalForm(),
						NewProjectDialog.class.getResource(DIALOG_STYLESHEET).toExternalForm(),
						NewProjectDialog.class.getResource(STYLESHEET).toExternalForm()),
				shell, "Create", () -> {
					ManifestSourcePage.Loaded data = loaded.get();
					if (data == null && source.hasInput()) {
						shell.select(manifestPage);
						source.showError("Press Load to import the manifest, or clear the field to create an empty project.");
						return;
					}
					create(general, generalPage, draft, pages, shell,
							(file, project) -> creator.create(file, imported(project, data, review, source)));
				}, "Cancel").show();
	}

	private static Project imported(Project project, ManifestSourcePage.Loaded data, ManifestReviewPage review,
			ManifestSourcePage source) {
		if (data == null || review.accepted().isEmpty()) {
			return project;
		}
		return ManifestImporter.withBaseUrl(ManifestReimport.apply(project, data.diff(), review.accepted()),
				source.baseUrl());
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
