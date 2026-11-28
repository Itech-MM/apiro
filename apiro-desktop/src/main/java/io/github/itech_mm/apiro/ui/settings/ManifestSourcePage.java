package io.github.itech_mm.apiro.ui.settings;

import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.service.manifest.ManifestDiff;
import io.github.itech_mm.apiro.service.manifest.ManifestException;
import io.github.itech_mm.apiro.service.manifest.ManifestFetcher;
import io.github.itech_mm.apiro.service.manifest.ManifestImporter;
import io.github.itech_mm.apiro.service.manifest.ManifestReader;
import io.github.itech_mm.apiro.service.manifest.ManifestReimport;
import io.github.itech_mm.apiro.spec.Manifest;
import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

public final class ManifestSourcePage {

    public record Loaded(Manifest manifest, ManifestDiff diff) {
    }

    private final Window owner;

    private final Supplier<Project> project;

    private final Consumer<Loaded> onLoaded;

    private final RadioButton fromApp = new RadioButton("Running application");

    private final RadioButton fromFile = new RadioButton("Manifest file");

    private final TextField addressField = new TextField();

    private final PasswordField tokenField = new PasswordField();

    private final TextField fileField = new TextField();

    private final TextField baseUrlField = new TextField();

    private final RadioButton compareAll = new RadioButton("Everything imported so far");

    private final RadioButton compareSome = new RadioButton("Only these folders");

    private final Map<String, CheckBox> folderBoxes = new LinkedHashMap<>();

    private final VBox folderList = new VBox(4);

    private final Button load = new Button("Load");

    private final ProgressIndicator spinner = new ProgressIndicator();

    private final Label status = new Label();

    private final Label error = new Label();

    private final VBox appGroup;

    private final VBox fileGroup;

    private final VBox root;

    public ManifestSourcePage(Window owner, Supplier<Project> project, Consumer<Loaded> onLoaded) {
        this.owner = owner;
        this.project = project;
        this.onLoaded = onLoaded;
        ToggleGroup sources = new ToggleGroup();
        fromApp.setToggleGroup(sources);
        fromFile.setToggleGroup(sources);
        fromApp.setSelected(true);
        addressField.setPromptText("http://localhost:8080/apiro/manifest");
        tokenField.setPromptText("Only if the application asks for one");
        fileField.setPromptText("orders.manifest.json");
        baseUrlField.setPromptText("http://localhost:8080");

        Button browse = new Button("Browse...");
        browse.getStyleClass().add("settings-browse-button");
        browse.setOnAction(event -> browse());
        HBox.setHgrow(fileField, Priority.ALWAYS);

        appGroup = new VBox(14, group("Address", addressField), group("Access token (optional)", tokenField));
        fileGroup = new VBox(14, group("File", new HBox(8, fileField, browse)));

        load.getStyleClass().add("settings-browse-button");
        load.setOnAction(event -> load());
        spinner.setPrefSize(16, 16);
        spinner.setMaxSize(16, 16);
        busy(false);
        status.getStyleClass().add("settings-page-hint");
        error.getStyleClass().add("settings-error");
        error.setWrapText(true);
        hide(error);
        HBox loadRow = new HBox(10, load, spinner, status);
        loadRow.setAlignment(Pos.CENTER_LEFT);

        root = new VBox(14, group("Read the manifest from", new HBox(18, fromApp, fromFile)), appGroup, fileGroup);
        List<ManifestReimport.LinkedFolder> linked = ManifestReimport.linkedFolders(project.get());
        if (!linked.isEmpty()) {
            root.getChildren().add(compareGroup(linked));
        }
        if (!ManifestImporter.hasBaseUrl(project.get())) {
            Label hint = new Label("Imported requests start with ${baseUrl}, which this project does not define yet. "
                    + "This value is added as a project variable. An environment can override it.");
            hint.setWrapText(true);
            hint.getStyleClass().add("settings-page-hint");
            root.getChildren().add(group("Base URL", baseUrlField, hint));
        }
        root.getChildren().addAll(loadRow, error);
        sources.selectedToggleProperty().addListener((obs, old, selected) -> showSource());
        showSource();
    }

    private VBox compareGroup(List<ManifestReimport.LinkedFolder> linked) {
        ToggleGroup compare = new ToggleGroup();
        compareAll.setToggleGroup(compare);
        compareSome.setToggleGroup(compare);
        compareAll.setSelected(true);
        for (ManifestReimport.LinkedFolder folder : linked) {
            folderBoxes.put(folder.id(), new CheckBox(folder.path() + "  (" + folder.requests() + ")"));
        }
        folderList.getChildren().addAll(folderBoxes.values());
        folderList.setPadding(new Insets(0, 0, 0, 22));
        showFolders();
        compare.selectedToggleProperty().addListener((obs, old, selected) -> showFolders());
        Label hint = new Label("Pick the folders that belong to this application when the project also holds "
                + "endpoints from another one. Requests in the other folders are left out, so they are not "
                + "listed as no longer in code.");
        hint.setWrapText(true);
        hint.getStyleClass().add("settings-page-hint");
        return group("Compare with", new HBox(18, compareAll, compareSome), folderList, hint);
    }

    private void showFolders() {
        boolean some = compareSome.isSelected();
        folderList.setVisible(some);
        folderList.setManaged(some);
    }

    /** Folder ids to compare with, or null to compare with everything imported so far. */
    private Set<String> scope() {
        if (!compareSome.isSelected()) {
            return null;
        }
        Set<String> ids = new LinkedHashSet<>();
        folderBoxes.forEach((id, box) -> {
            if (box.isSelected()) {
                ids.add(id);
            }
        });
        return ids;
    }

    public SettingsPage page() {
        return new SettingsPage("Source", root);
    }

    public String baseUrl() {
        return baseUrlField.getText() == null ? "" : baseUrlField.getText().trim();
    }

    public boolean hasInput() {
        TextField field = fromApp.isSelected() ? addressField : fileField;
        return field.getText() != null && !field.getText().isBlank();
    }

    public void showError(String message) {
        error.setText(message);
        error.setVisible(true);
        error.setManaged(true);
    }

    private void showSource() {
        boolean app = fromApp.isSelected();
        appGroup.setVisible(app);
        appGroup.setManaged(app);
        fileGroup.setVisible(!app);
        fileGroup.setManaged(!app);
    }

    private void load() {
        hide(error);
        status.setText("");
        boolean app = fromApp.isSelected();
        String address = addressField.getText();
        String token = tokenField.getText();
        String file = fileField.getText() == null ? "" : fileField.getText().trim();
        if (!app && file.isEmpty()) {
            showError("Choose a manifest file.");
            return;
        }
        Set<String> scope = scope();
        if (scope != null && scope.isEmpty()) {
            showError("Tick at least one folder to compare with, or choose Everything imported so far.");
            return;
        }
        Project snapshot = project.get();
        Task<Loaded> task = new Task<>() {
            @Override
            protected Loaded call() throws Exception {
                Manifest manifest = app ? ManifestFetcher.fetch(address, token) : ManifestReader.read(Path.of(file));
                return new Loaded(manifest, ManifestReimport.diff(snapshot, manifest, scope));
            }
        };
        busy(true);
        task.setOnSucceeded(event -> {
            busy(false);
            Loaded loaded = task.getValue();
            if (app && baseUrl().isEmpty()) {
                baseUrlField.setText(ManifestFetcher.origin(address));
            }
            status.setText("Loaded " + loaded.manifest().getEndpoints().size() + " endpoints");
            onLoaded.accept(loaded);
        });
        task.setOnFailed(event -> {
            busy(false);
            showError(describe(task.getException()));
        });
        Thread worker = new Thread(task, "manifest-load");
        worker.setDaemon(true);
        worker.start();
    }

    private void busy(boolean on) {
        spinner.setVisible(on);
        spinner.setManaged(on);
        load.setDisable(on);
    }

    private void browse() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Manifest file");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File current = new File(fileField.getText() == null ? "" : fileField.getText().trim());
        File folder = current.isDirectory() ? current : current.getParentFile();
        if (folder != null && folder.isDirectory()) {
            chooser.setInitialDirectory(folder);
        }
        File chosen = chooser.showOpenDialog(owner);
        if (chosen != null) {
            fileField.setText(chosen.getAbsolutePath());
        }
    }

    private static String describe(Throwable failure) {
        if (failure instanceof ManifestException) {
            return failure.getMessage();
        }
        if (failure instanceof NoSuchFileException) {
            return "The file does not exist.";
        }
        if (failure instanceof IOException) {
            return "Could not read the file: " + failure.getMessage();
        }
        if (failure == null) {
            return "Loading failed.";
        }
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static void hide(Label label) {
        label.setVisible(false);
        label.setManaged(false);
    }

    private static VBox group(String title, Node... fields) {
        Label label = new Label(title);
        label.getStyleClass().add("settings-field-label");
        VBox box = new VBox(4, label);
        box.getChildren().addAll(fields);
        return box;
    }
}
