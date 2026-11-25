package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_framework.code_editor.CodeEditor;
import io.github.itech_framework.java_fx.ui.dialog.AlertDialog;
import io.github.itech_framework.java_fx.ui.dialog.ModalDialog;
import io.github.itech_mm.apiro.model.RunRecord;
import io.github.itech_mm.apiro.service.scenario.RunRecords;
import io.github.itech_mm.apiro.service.scenario.RunReport;
import io.github.itech_mm.apiro.ui.EditorStyle;
import io.github.itech_mm.apiro.ui.editor.ScenarioLogLanguage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

final class HistoryPane extends SplitPane {

    private final ObservableList<RunRecord> items = FXCollections.observableArrayList();
    private final ListView<RunRecord> list = new ListView<>(items);
    private final Label heading = new Label();
    private final CodeEditor viewer = new CodeEditor();
    private final Button export = new Button("Export");
    private final ProgressBar progress = new ProgressBar();
    private final Label outcome = new Label();
    private final HBox status = new HBox(8, progress, outcome);

    HistoryPane(Consumer<RunRecord> onOpen) {
        getStyleClass().add("main-split");
        list.getStyleClass().add("scenario-list");
        list.setPlaceholder(new Label("No saved runs yet"));
        list.setCellFactory(view -> new HistoryCell());
        list.setTooltip(new Tooltip("Double-click a run to show its results on the diagram"));
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> showRun(newValue));
        list.setOnMouseClicked(event -> {
            RunRecord picked = list.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 2 && picked != null) {
                onOpen.accept(picked);
            }
        });

        ScenarioLogLanguage.ensureRegistered();
        EditorStyle.apply(viewer);
        viewer.setLanguage(ScenarioLogLanguage.ID);
        viewer.setEditable(false);
        viewer.setShowLineNumbers(false);
        viewer.getStyleClass().add("scenario-log");
        heading.getStyleClass().add("muted");
        heading.setPadding(new Insets(8, 10, 6, 10));
        export.getStyleClass().add("link-button");
        export.setDisable(true);
        export.setOnAction(event -> chooseAndExport());
        Label runs = new Label("Runs");
        runs.getStyleClass().add("muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(runs, spacer, export);
        head.setAlignment(Pos.CENTER_LEFT);
        head.setPadding(new Insets(4, 6, 4, 10));
        progress.setPrefWidth(80);
        progress.setPrefHeight(10);
        status.setAlignment(Pos.CENTER_LEFT);
        status.setPadding(new Insets(4, 10, 4, 10));
        status.setVisible(false);
        status.setManaged(false);
        VBox left = new VBox(head, list, status);
        left.setMinWidth(0);
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox right = new VBox(heading, viewer);
        right.setMinWidth(0);
        VBox.setVgrow(viewer, Priority.ALWAYS);
        list.setMinWidth(0);
        getItems().addAll(left, right);
        setDividerPositions(0.32);
    }

    void show(List<RunRecord> runs) {
        items.setAll(runs);
        if (runs.isEmpty()) {
            showRun(null);
        } else {
            list.getSelectionModel().select(0);
        }
    }

    private void showRun(RunRecord run) {
        export.setDisable(run == null);
        if (run == null) {
            heading.setText("");
            viewer.setText("");
            return;
        }
        String environment = run.environmentName().isBlank() ? "" : "  ·  " + run.environmentName();
        heading.setText(RunRecords.label(run, ZoneId.systemDefault()) + environment);
        viewer.setText(run.log());
        viewer.moveCaret(0);
    }

    private void chooseAndExport() {
        RunRecord run = list.getSelectionModel().getSelectedItem();
        if (run == null) {
            return;
        }
        Label title = new Label("Export run");
        title.getStyleClass().add("alert-title");
        Label caption = new Label(RunRecords.label(run, ZoneId.systemDefault()));
        caption.getStyleClass().add("alert-message");
        ToggleGroup group = new ToggleGroup();
        RadioButton junit = new RadioButton("JUnit XML (for CI and test viewers)");
        junit.setToggleGroup(group);
        junit.setUserData("xml");
        RadioButton html = new RadioButton("HTML report (to read or share)");
        html.setToggleGroup(group);
        html.setUserData("html");
        junit.setSelected(true);
        boolean[] confirmed = new boolean[1];
        Runnable accept = () -> {
            confirmed[0] = true;
            ModalDialog current = ModalDialog.getCurrentDialog();
            if (current != null) {
                current.close();
            }
        };
        ModalDialog.builder().addOwner(owner())
                .stylesheets(HistoryPane.class.getResource("/static/css/app.css").toExternalForm(),
                        HistoryPane.class.getResource("/static/css/dialog.css").toExternalForm())
                .addContent(new VBox(8, title, caption, junit, html)).addActionButton("Export...", accept)
                .addCloseButton("Cancel").build().show();
        if (!confirmed[0]) {
            return;
        }
        String extension = String.valueOf(group.getSelectedToggle().getUserData());
        String name = extension.equals("xml") ? "JUnit XML" : "HTML report";
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export " + name);
        chooser.setInitialFileName(RunReport.fileName(run, extension));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(name, "*." + extension));
        File target = chooser.showSaveDialog(owner());
        if (target != null) {
            write(run, extension, target);
        }
    }

    private Stage owner() {
        return getScene() != null && getScene().getWindow() instanceof Stage stage ? stage : null;
    }

    private void write(RunRecord run, String extension, File target) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                String content = extension.equals("xml") ? RunReport.junitXml(run)
                        : RunReport.html(run, ZoneId.systemDefault());
                Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
                return null;
            }
        };
        showStatus(true, "Exporting " + target.getName() + "...", "");
        export.setDisable(true);
        task.setOnSucceeded(event -> {
            export.setDisable(list.getSelectionModel().getSelectedItem() == null);
            showStatus(false, "Exported to " + target.getName(), "state-passed");
            hideLater();
        });
        task.setOnFailed(event -> {
            export.setDisable(list.getSelectionModel().getSelectedItem() == null);
            Throwable error = task.getException();
            String reason = error == null || error.getMessage() == null ? "unknown error" : error.getMessage();
            showStatus(false, "Export failed: " + reason, "state-failed");
            AlertDialog.builder().level(AlertDialog.Level.ERROR).title("Export failed")
                    .message("Could not write " + target.getName() + ": " + reason).addOwner(owner())
                    .addButton("OK", null).build().showAsync();
        });
        Thread worker = new Thread(task, "run-export");
        worker.setDaemon(true);
        worker.start();
    }

    private void showStatus(boolean busy, String text, String style) {
        progress.setVisible(busy);
        progress.setManaged(busy);
        progress.setProgress(busy ? ProgressBar.INDETERMINATE_PROGRESS : 1);
        outcome.setText(text);
        outcome.getStyleClass().removeAll("state-passed", "state-failed", "muted");
        if (!outcome.getStyleClass().contains("export-status")) {
            outcome.getStyleClass().add("export-status");
        }
        outcome.getStyleClass().add(style.isEmpty() ? "muted" : style);
        status.setManaged(true);
        status.setVisible(true);
    }

    private void hideLater() {
        Thread timer = new Thread(() -> {
            try {
                Thread.sleep(6000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Platform.runLater(() -> {
                if (!progress.isVisible()) {
                    status.setVisible(false);
                    status.setManaged(false);
                }
            });
        }, "run-export-hide");
        timer.setDaemon(true);
        timer.start();
    }

    private static String stateClass(String state) {
        String lower = state.toLowerCase(Locale.ROOT);
        return lower.equals("passed") || lower.equals("cancelled") ? lower : "failed";
    }

    private static final class HistoryCell extends ListCell<RunRecord> {

        private final Label dot = new Label();
        private final Label when = new Label();
        private final Label brief = new Label();
        private final HBox row;

        HistoryCell() {
            dot.getStyleClass().add("step-dot");
            brief.getStyleClass().add("muted");
            VBox text = new VBox(1, when, brief);
            row = new HBox(8, dot, text);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(3, 6, 3, 6));
        }

        @Override
        protected void updateItem(RunRecord run, boolean empty) {
            super.updateItem(run, empty);
            if (empty || run == null) {
                setGraphic(null);
                return;
            }
            dot.getStyleClass().removeIf(style -> style.startsWith("state-"));
            dot.getStyleClass().add("state-" + stateClass(run.state()));
            when.setText(RunRecords.when(run, ZoneId.systemDefault()));
            brief.setText(RunRecords.brief(run));
            setGraphic(row);
        }
    }
}
