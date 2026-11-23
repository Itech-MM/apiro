package io.github.itech_mm.apiro.ui.settings;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/** First page of the new project dialog: name, folder, resulting file and description. */
public final class NewProjectGeneralPage {

	static final String EXTENSION = ".apiro.json";

	private final ProjectDraft draft;

	private final Window owner;

	private final TextField nameField = new TextField();

	private final TextField locationField = new TextField();

	private final TextField fileField = new TextField();

	private final TextArea descriptionArea = new TextArea();

	private final Label locationHint = new Label();

	private final Label error = new Label();

	private final VBox root;

	private String serverError = "";

	public NewProjectGeneralPage(ProjectDraft draft, Window owner, Path defaultLocation, String defaultName) {
		this.draft = draft;
		this.owner = owner;
		nameField.setText(defaultName);
		nameField.setPromptText("My API");
		locationField.setText(defaultLocation == null ? "" : defaultLocation.toString());
		fileField.setEditable(false);
		fileField.setFocusTraversable(false);
		descriptionArea.setPrefRowCount(4);
		descriptionArea.setWrapText(true);

		Button browse = new Button("Browse...");
		browse.getStyleClass().add("settings-browse-button");
		browse.setOnAction(event -> browse());
		HBox.setHgrow(locationField, Priority.ALWAYS);
		HBox locationRow = new HBox(8, locationField, browse);

		locationHint.getStyleClass().add("settings-page-hint");
		locationHint.setWrapText(true);
		error.getStyleClass().add("settings-error");
		error.setWrapText(true);

		root = new VBox(14, group("Name", nameField), group("Location", locationRow, locationHint),
				group("Project file", fileField), group("Description", descriptionArea), error);

		nameField.textProperty().addListener((obs, old, text) -> edited());
		locationField.textProperty().addListener((obs, old, text) -> edited());
		refresh();
	}

	public SettingsPage page() {
		return new SettingsPage("General", root, this::commit, this::problem);
	}

	/** The file the project will be written to, or null while name or location is unusable. */
	public Path targetFile() {
		String stem = fileStem();
		String folder = locationField.getText().trim();
		if (stem.isEmpty() || folder.isEmpty()) {
			return null;
		}
		try {
			return Path.of(folder).resolve(stem + EXTENSION);
		} catch (InvalidPathException e) {
			return null;
		}
	}

	/** Shows a failure from writing the file, until the person edits the name or location. */
	public void showError(String message) {
		serverError = message == null ? "" : message;
		refresh();
	}

	private void commit() {
		draft.setName(nameField.getText().trim());
		draft.setDescription(descriptionArea.getText());
	}

	private String problem() {
		if (!serverError.isEmpty()) {
			return serverError;
		}
		if (fileStem().isEmpty()) {
			return "Enter a project name.";
		}
		String folder = locationField.getText().trim();
		if (folder.isEmpty()) {
			return "Choose a location for the project file.";
		}
		Path target = targetFile();
		if (target == null) {
			return "The location is not a valid path.";
		}
		Path parent = target.getParent();
		if (parent != null && Files.exists(parent) && !Files.isDirectory(parent)) {
			return "The location is a file, not a folder.";
		}
		if (Files.exists(target)) {
			return "A file named " + target.getFileName() + " already exists in this location.";
		}
		return "";
	}

	private void edited() {
		serverError = "";
		refresh();
	}

	private void refresh() {
		Path target = targetFile();
		fileField.setText(target == null ? "" : target.toString());
		Path parent = target == null ? null : target.getParent();
		boolean missing = parent != null && !Files.exists(parent);
		locationHint.setText(missing ? "The folder does not exist yet and will be created." : "");
		locationHint.setVisible(missing);
		locationHint.setManaged(missing);
		String problem = problem();
		error.setText(problem);
		error.setVisible(!problem.isEmpty());
		error.setManaged(!problem.isEmpty());
	}

	private void browse() {
		DirectoryChooser chooser = new DirectoryChooser();
		chooser.setTitle("Project location");
		File current = new File(locationField.getText().trim());
		if (current.isDirectory()) {
			chooser.setInitialDirectory(current);
		}
		File chosen = chooser.showDialog(owner);
		if (chosen != null) {
			locationField.setText(chosen.getAbsolutePath());
		}
	}

	/** Name made safe to use as a file name; empty when nothing usable is left. */
	private String fileStem() {
		String stem = nameField.getText().trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
		while (stem.endsWith(".") || stem.endsWith(" ")) {
			stem = stem.substring(0, stem.length() - 1);
		}
		return stem;
	}

	private static VBox group(String title, Node... fields) {
		Label label = new Label(title);
		label.getStyleClass().add("settings-field-label");
		VBox box = new VBox(4, label);
		box.getChildren().addAll(fields);
		return box;
	}
}
