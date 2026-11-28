package io.github.itech_mm.apiro.ui.settings;

import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.service.manifest.ManifestDiff;
import io.github.itech_mm.apiro.service.manifest.ManifestImporter;
import io.github.itech_mm.apiro.service.manifest.ManifestReimport;
import io.github.itech_mm.apiro.session.ProjectSession;
import io.github.itech_mm.apiro.ui.OpenRequests;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class ImportManifestDialog {

    private static final String APP_STYLESHEET = "/static/css/app.css";

    private static final String STYLESHEET = "/static/css/settings.css";

    private static final String DIALOG_STYLESHEET = "/static/css/dialog.css";

    private ImportManifestDialog() {
    }

    public static void open(Window owner, ProjectSession session, OpenRequests tabs, Consumer<String> done) {
        Project project = session.project();
        if (project == null) {
            return;
        }
        Set<String> blocked = tabs.dirtyIds();
        ManifestReviewPage review = new ManifestReviewPage();
        SettingsPage reviewPage = review.page();
        AtomicReference<ManifestSourcePage.Loaded> loaded = new AtomicReference<>();
        AtomicReference<SettingsShell> shellRef = new AtomicReference<>();
        ManifestSourcePage source = new ManifestSourcePage(owner, session::project, data -> {
            loaded.set(data);
            review.show(data.diff(), blocked);
            shellRef.get().select(reviewPage);
        });
        SettingsPage sourcePage = source.page();
        List<SettingsPage> pages = List.of(sourcePage, reviewPage);
        String projectName = project.name() == null || project.name().isBlank() ? "Untitled" : project.name();
        SettingsShell shell = new SettingsShell("Import Endpoints", projectName, "MANIFEST", pages);
        shellRef.set(shell);
        SettingsModal.create(owner instanceof Stage stage ? stage : null,
                List.of(ImportManifestDialog.class.getResource(APP_STYLESHEET).toExternalForm(),
                        ImportManifestDialog.class.getResource(DIALOG_STYLESHEET).toExternalForm(),
                        ImportManifestDialog.class.getResource(STYLESHEET).toExternalForm()),
                shell, "Import",
                () -> apply(session, tabs, done, loaded.get(), review, source, sourcePage, shell), "Cancel").show();
    }

    private static void apply(ProjectSession session, OpenRequests tabs, Consumer<String> done,
            ManifestSourcePage.Loaded data, ManifestReviewPage review, ManifestSourcePage source,
            SettingsPage sourcePage, SettingsShell shell) {
        if (data == null) {
            shell.select(sourcePage);
            source.showError("Load a manifest first.");
            return;
        }
        ManifestDiff diff = data.diff();
        Set<String> accepted = review.accepted();
        if (accepted.isEmpty()) {
            closeCurrent();
            done.accept("Nothing imported");
            return;
        }
        String baseUrl = source.baseUrl();
        Set<String> updatedIds = diff.updated().stream().filter(entry -> accepted.contains(entry.key()))
                .map(ManifestDiff.Updated::requestId).collect(Collectors.toSet());
        long added = diff.added().stream().filter(entry -> accepted.contains(entry.key())).count();
        session.updateProject(project -> ManifestImporter
                .withBaseUrl(ManifestReimport.apply(project, diff, accepted), baseUrl));
        tabs.reload(updatedIds);
        closeCurrent();
        done.accept("Imported " + added + " new and updated " + updatedIds.size() + " requests.");
    }

    private static void closeCurrent() {
        SettingsModal dialog = SettingsModal.current();
        if (dialog != null) {
            dialog.close();
        }
    }
}
