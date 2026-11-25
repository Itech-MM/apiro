package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_framework.code_editor.CodeEditor;
import io.github.itech_mm.apiro.model.RunRecord;
import io.github.itech_mm.apiro.service.scenario.RunRecords;
import io.github.itech_mm.apiro.ui.EditorStyle;
import io.github.itech_mm.apiro.ui.editor.ScenarioLogLanguage;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class HistoryPane extends SplitPane {

    private final ObservableList<RunRecord> items = FXCollections.observableArrayList();
    private final ListView<RunRecord> list = new ListView<>(items);
    private final Label heading = new Label();
    private final CodeEditor viewer = new CodeEditor();

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
        VBox right = new VBox(heading, viewer);
        right.setMinWidth(0);
        VBox.setVgrow(viewer, Priority.ALWAYS);
        list.setMinWidth(0);
        getItems().addAll(list, right);
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
