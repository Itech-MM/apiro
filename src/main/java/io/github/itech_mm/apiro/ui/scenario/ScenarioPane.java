package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_framework.api_client.utils.CancelToken;
import io.github.itech_framework.code_editor.CodeEditor;
import io.github.itech_mm.apiro.model.Environment;
import io.github.itech_mm.apiro.model.OnFailure;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.RunRecord;
import io.github.itech_mm.apiro.model.RunStep;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.service.scenario.RunLog;
import io.github.itech_mm.apiro.service.scenario.RunRecords;
import io.github.itech_mm.apiro.service.scenario.RunStore;
import io.github.itech_mm.apiro.service.scenario.ScenarioEdit;
import io.github.itech_mm.apiro.service.scenario.ScenarioHistory;
import io.github.itech_mm.apiro.service.scenario.ScenarioListener;
import io.github.itech_mm.apiro.service.scenario.ScenarioResult;
import io.github.itech_mm.apiro.service.scenario.ScenarioRunner;
import io.github.itech_mm.apiro.service.scenario.ScenarioScope;
import io.github.itech_mm.apiro.service.scenario.StepResult;
import io.github.itech_mm.apiro.service.scenario.StepSummary;
import io.github.itech_mm.apiro.service.scenario.StepSender;
import io.github.itech_mm.apiro.service.variables.VariableScope;
import io.github.itech_mm.apiro.ui.EditorStyle;
import io.github.itech_mm.apiro.ui.editor.ScenarioLogLanguage;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class ScenarioPane extends BorderPane {

    public interface Host {

        Project project();

        String environmentId();

        StepSender sender();

        void save(ScenarioItem scenario);

        RunStore runStore();
    }

    private static final double DIAGRAM_SPLIT = 0.70;
    private static final double LIST_SPLIT = 0.5;
    private static final double LOG_SPLIT = 0.74;
    private static final String STOP = "Stop";
    private static final String CONTINUE = "Continue";

    private static final class RunView {

        final Map<String, StepResult> results = new HashMap<>();
        final Map<String, StepResult.State> states = new HashMap<>();
        final Map<String, RunStep> recorded = new HashMap<>();
        final StringBuilder log = new StringBuilder();
        CancelToken token;
        boolean running;
        String summary = "";
    }

    private final Host host;
    private final Map<String, RunView> runs = new HashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "scenario-run");
        thread.setDaemon(true);
        return thread;
    });
    private final Label title = new Label();
    private final Label summary = new Label();
    private final ComboBox<String> policy = new ComboBox<>(FXCollections.observableArrayList(STOP, CONTINUE));
    private final MenuButton addStep = new MenuButton("Add step");
    private final Map<String, List<RunRecord>> saved = new HashMap<>();
    private final HistoryPane historyPane = new HistoryPane(this::openRun);
    private final Button clearHistoryButton = new Button("Clear history");
    private final ToggleButton liveToggle = new ToggleButton("Log");
    private final ToggleButton historyToggle = new ToggleButton("History");
    private VBox logPanel;
    private final Button run = new Button("Run");
    private final Button stop = new Button("Stop");
    private final ObservableList<Step> items = FXCollections.observableArrayList();
    private final ListView<Step> list = new ListView<>(items);
    private final StepInspector inspector;
    private final DiagramView diagram;
    private final CodeEditor log = new CodeEditor();
    private ScenarioItem current;
    private boolean loading;
    private boolean syncing;
    private boolean diagramShown = true;
    private boolean splitPending = true;
    private boolean logShown = true;
    private SplitPane horizontal;
    private SplitPane vertical;
    private final Map<String, ScenarioHistory> histories = new HashMap<>();
    private final Button undoButton = new Button("Undo");
    private final Button redoButton = new Button("Redo");

    public ScenarioPane(Host host) {
        this.host = host;
        getStyleClass().add("scenario-pane");
        inspector = new StepInspector(this::onStepEdited);
        diagram = new DiagramView(support(), this::selectById, new DiagramView.Actions() {
            @Override
            public Optional<RequestItem> dropped(Dragboard board) {
                return droppedRequest(board);
            }

            @Override
            public void add(String requestId, int index) {
                insertAt(requestId, index);
            }

            @Override
            public void undo() {
                ScenarioPane.this.undo();
            }

            @Override
            public void redo() {
                ScenarioPane.this.redo();
            }

            @Override
            public void run() {
                startRun();
            }

            @Override
            public void stop() {
                cancelRun();
            }

            @Override
            public void log(boolean shown) {
                showLog(shown);
            }

            @Override
            public void focusInspector() {
                Platform.runLater(inspector::focusName);
            }
        });

        title.getStyleClass().add("scenario-title");
        summary.getStyleClass().add("muted");
        policy.setTooltip(new Tooltip("What happens after a step fails"));
        policy.getStyleClass().add("bar-combo");
        addStep.getStyleClass().add("scenario-add");
        Label policyLabel = new Label("On failure");
        policyLabel.getStyleClass().add("muted");
        run.getStyleClass().add("scenario-run");
        stop.getStyleClass().add("scenario-stop");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        undoButton.setTooltip(new Tooltip("Undo (Ctrl+Z)"));
        redoButton.setTooltip(new Tooltip("Redo (Ctrl+Y)"));
        undoButton.setOnAction(event -> undo());
        redoButton.setOnAction(event -> redo());
        HBox bar = new HBox(8, title, summary, spacer, policyLabel, policy, undoButton, redoButton, addStep, run, stop);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 12, 10, 12));
        bar.getStyleClass().add("scenario-bar");

        list.setPlaceholder(new Label("Drag requests here or use Add step"));
        list.setCellFactory(view -> new StepCell(support()));
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (!syncing) {
                inspectSelected();
            }
        });
        list.setOnDragOver(event -> {
            if (droppedRequest(event.getDragboard()).isPresent()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
        });
        list.setOnDragDropped(event -> {
            Optional<RequestItem> request = droppedRequest(event.getDragboard());
            if (request.isPresent()) {
                append(request.get().id());
                event.setDropCompleted(true);
            }
            event.consume();
        });

        ScenarioLogLanguage.ensureRegistered();
        EditorStyle.apply(log);
        log.setLanguage(ScenarioLogLanguage.ID);
        log.setEditable(false);
        log.setShowLineNumbers(false);
        log.setMinHeight(90);
        log.getStyleClass().add("scenario-log");

        list.getStyleClass().add("scenario-list");
        list.setOnKeyPressed(event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.Z && !event.isShiftDown()) {
                undo();
                event.consume();
            } else if (event.isShortcutDown() && (event.getCode() == KeyCode.Y
                    || event.getCode() == KeyCode.Z && event.isShiftDown())) {
                redo();
                event.consume();
            }
        });
        ToggleButton diagramToggle = new ToggleButton("Diagram");
        ToggleButton listToggle = new ToggleButton("List");
        ToggleGroup group = new ToggleGroup();
        diagramToggle.setToggleGroup(group);
        listToggle.setToggleGroup(group);
        group.selectedToggleProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) {
                oldValue.setSelected(true);
            }
        });
        HBox switcher = new HBox(diagramToggle, listToggle);
        switcher.getStyleClass().add("view-switch");
        switcher.setMaxWidth(Region.USE_PREF_SIZE);
        HBox viewHeader = new HBox(switcher);
        viewHeader.getStyleClass().add("view-header");
        viewHeader.setAlignment(Pos.CENTER_LEFT);
        StackPane stack = new StackPane(list, diagram);
        VBox.setVgrow(stack, Priority.ALWAYS);
        VBox views = new VBox(viewHeader, stack);
        views.setMinWidth(0);
        horizontal = new SplitPane(views, inspector);
        horizontal.getStyleClass().add("main-split");
        group.selectedToggleProperty().addListener((obs, oldValue, newValue) -> {
            boolean showDiagram = newValue == diagramToggle;
            diagramShown = showDiagram;
            diagram.setVisible(showDiagram);
            diagram.setManaged(showDiagram);
            list.setVisible(!showDiagram);
            list.setManaged(!showDiagram);
            applySplit();
            if (showDiagram) {
                diagram.refit();
            }
        });
        diagramToggle.setSelected(true);
        vertical = new SplitPane(horizontal, buildLogPanel());
        vertical.setOrientation(Orientation.VERTICAL);
        vertical.getStyleClass().add("editor-split");
        horizontal.widthProperty().addListener((obs, oldValue, newValue) -> {
            if (splitPending && newValue.doubleValue() > 200) {
                splitPending = false;
                applySplit();
            }
        });
        applySplit();
        setTop(bar);
        setCenter(vertical);

        policy.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!loading && current != null && newValue != null) {
                commit(ScenarioEdit.withOnFailure(current, CONTINUE.equals(newValue) ? OnFailure.CONTINUE
                        : OnFailure.STOP), false);
            }
        });
        addStep.setOnShowing(event -> fillAddMenu());
        run.setOnAction(event -> startRun());
        stop.setOnAction(event -> cancelRun());
        list.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) {
                Platform.runLater(inspector::focusName);
            }
        });
        refreshRunState();
        diagram.syncLog(logShown);
    }

    private void cancelRun() {
        RunView view = viewOf(current);
        if (view != null && view.token != null) {
            view.token.cancel();
        }
    }

    private void showLog(boolean shown) {
        diagram.syncLog(shown);
        if (logShown == shown) {
            return;
        }
        logShown = shown;
        if (shown) {
            vertical.getItems().add(logPanel);
            applySplit();
        } else {
            vertical.getItems().remove(logPanel);
        }
    }

    private void applySplit() {
        Platform.runLater(() -> {
            horizontal.setDividerPositions(diagramShown ? DIAGRAM_SPLIT : LIST_SPLIT);
            if (logShown) {
                vertical.setDividerPositions(LOG_SPLIT);
            }
        });
    }

    private ScenarioHistory history() {
        return histories.computeIfAbsent(current.id(), key -> new ScenarioHistory());
    }

    private void undo() {
        if (current != null) {
            history().undo(current).ifPresent(this::restore);
        }
    }

    private void redo() {
        if (current != null) {
            history().redo(current).ifPresent(this::restore);
        }
    }

    private void restore(ScenarioItem snapshot) {
        commit(ScenarioEdit.withContent(current, snapshot), true, false);
    }

    public void show(ScenarioItem scenario) {
        boolean same = current != null && current.id().equals(scenario.id());
        if (same && current.equals(scenario)) {
            list.refresh();
            diagram.refresh();
            return;
        }
        String keep = same ? selectedId() : null;
        current = scenario;
        restoreLastRun();
        if (!same) {
            applySplit();
        }
        loading = true;
        title.setText(scenario.name());
        policy.setValue(scenario.onFailure() == OnFailure.CONTINUE ? CONTINUE : STOP);
        loading = false;
        syncing = true;
        items.setAll(scenario.steps());
        selectById(keep);
        syncing = false;
        inspectSelected();
        diagram.show(scenario.steps(), selectedId(), !same);
        refreshRunState();
        refreshLog();
        if (!same) {
            refreshHistory();
        }
    }

    private void onStepEdited(Step step) {
        if (current == null) {
            return;
        }
        ScenarioItem next = ScenarioEdit.withStep(current, step);
        if (next == current) {
            return;
        }
        history().record(current, "step:" + step.id(), System.currentTimeMillis());
        current = next;
        syncing = true;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(step.id())) {
                items.set(i, step);
                list.getSelectionModel().select(i);
                break;
            }
        }
        syncing = false;
        diagram.show(current.steps(), selectedId(), false);
        host.save(next);
        refreshRunState();
    }

    private void commit(ScenarioItem next, boolean rebuild) {
        commit(next, rebuild, true);
    }

    private void commit(ScenarioItem next, boolean rebuild, boolean record) {
        if (next == current) {
            return;
        }
        String keep = selectedId();
        if (record) {
            history().record(current, null, System.currentTimeMillis());
        }
        current = next;
        if (rebuild) {
            syncing = true;
            items.setAll(next.steps());
            selectById(keep);
            syncing = false;
            inspectSelected();
            diagram.show(next.steps(), selectedId(), false);
        }
        host.save(next);
        refreshRunState();
    }

    private void append(String requestId) {
        if (current == null) {
            return;
        }
        ScenarioItem next = ScenarioEdit.withAppended(current, requestId, "");
        commit(next, true);
        selectById(next.steps().get(next.steps().size() - 1).id());
    }

    private void insertAt(String requestId, int index) {
        if (current == null) {
            return;
        }
        ScenarioItem next = ScenarioEdit.withInserted(current, requestId, "", index);
        commit(next, true);
        selectById(next.steps().get(Math.max(0, Math.min(index, next.steps().size() - 1))).id());
    }

    private Optional<RequestItem> droppedRequest(Dragboard board) {
        Project project = host.project();
        if (project == null || !board.hasString()) {
            return Optional.empty();
        }
        return project.findItem(board.getString()).filter(RequestItem.class::isInstance).map(RequestItem.class::cast);
    }

    private void fillAddMenu() {
        addStep.getItems().clear();
        Project project = host.project();
        List<ScenarioEdit.RequestRef> requests = project == null ? List.of() : ScenarioEdit.requests(project);
        if (requests.isEmpty()) {
            MenuItem none = new MenuItem("No requests in this project");
            none.setDisable(true);
            addStep.getItems().add(none);
            return;
        }
        for (ScenarioEdit.RequestRef ref : requests) {
            MenuItem entry = new MenuItem(ref.method().name() + "  " + ref.label());
            entry.setOnAction(event -> append(ref.id()));
            addStep.getItems().add(entry);
        }
    }

    public void refreshScope() {
        Step selected = list.getSelectionModel().getSelectedItem();
        Project project = host.project();
        if (current == null || selected == null || project == null) {
            VariableScope.update(null);
            return;
        }
        try {
            VariableScope.update(ScenarioScope.forStep(project, host.environmentId(), current, selected));
        } catch (RuntimeException error) {
            VariableScope.update(null);
        }
    }

    private void inspectSelected() {
        Step selected = list.getSelectionModel().getSelectedItem();
        diagram.select(selected == null ? null : selected.id());
        refreshScope();
        if (selected != null && selected.equals(inspector.step())) {
            return;
        }
        if (selected == null) {
            inspector.show(null, "", null);
            return;
        }
        inspector.show(selected, requestLabel(selected), inspectedResult(selected.id()));
    }

    private String requestLabel(Step step) {
        return request(step.requestId()).map(RequestItem::name).orElse("missing request");
    }

    private Optional<RequestItem> request(String requestId) {
        Project project = host.project();
        if (project == null) {
            return Optional.empty();
        }
        return project.findItem(requestId).filter(RequestItem.class::isInstance).map(RequestItem.class::cast);
    }

    private String selectedId() {
        Step selected = list.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.id();
    }

    private void selectById(String stepId) {
        if (stepId != null) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).id().equals(stepId)) {
                    list.getSelectionModel().select(i);
                    return;
                }
            }
        }
        if (!items.isEmpty()) {
            list.getSelectionModel().select(0);
        } else {
            list.getSelectionModel().clearSelection();
        }
    }

    private static List<RunRecord> persist(RunStore store, ScenarioItem scenario, String environmentName,
                                           long startedAt, ScenarioResult result, Map<String, String> labels) {
        try {
            return store.add(scenario.id(), RunRecords.of(scenario, environmentName, startedAt, result,
                    step -> labels.getOrDefault(step.id(), RunLog.label(step))));
        } catch (RuntimeException error) {
            return null;
        }
    }

    private Map<String, String> stepLabels(ScenarioItem scenario) {
        Map<String, String> labels = new HashMap<>();
        for (Step step : scenario.steps()) {
            labels.put(step.id(), step.name().isBlank()
                    ? request(step.requestId()).map(RequestItem::name).orElse(RunLog.label(step))
                    : step.name());
        }
        return labels;
    }

    private List<RunRecord> savedRuns(ScenarioItem scenario) {
        List<RunRecord> known = saved.get(scenario.id());
        if (known != null) {
            return known;
        }
        RunStore store = host.runStore();
        List<RunRecord> loaded = store.load(scenario.id());
        if (store != RunStore.NONE) {
            saved.put(scenario.id(), loaded);
        }
        return loaded;
    }

    private void restoreLastRun() {
        if (current == null || runs.containsKey(current.id())) {
            return;
        }
        List<RunRecord> recorded = savedRuns(current);
        if (!recorded.isEmpty()) {
            runs.put(current.id(), viewFrom(recorded.get(0)));
        }
    }

    private RunView viewFrom(RunRecord run) {
        RunView view = new RunView();
        view.log.append("Saved run from ").append(RunRecords.when(run, ZoneId.systemDefault())).append('\n')
                .append(run.log());
        for (RunStep step : run.steps()) {
            view.states.put(step.stepId(), RunRecords.stepState(step));
            view.recorded.put(step.stepId(), step);
        }
        view.summary = RunRecords.label(run, ZoneId.systemDefault());
        return view;
    }

    private RunStep recordedOf(String stepId) {
        RunView view = viewOf(current);
        return view == null ? null : view.recorded.get(stepId);
    }

    private StepResult inspectedResult(String stepId) {
        StepResult result = resultOf(stepId);
        if (result != null) {
            return result;
        }
        RunStep recorded = recordedOf(stepId);
        if (recorded == null || current == null) {
            return null;
        }
        for (Step step : current.steps()) {
            if (step.id().equals(stepId)) {
                return new StepResult(step, RunRecords.stepState(recorded), recorded.message(), recorded.millis(),
                        null, null, null);
            }
        }
        return null;
    }

    private VBox buildLogPanel() {
        ToggleGroup group = new ToggleGroup();
        liveToggle.setToggleGroup(group);
        historyToggle.setToggleGroup(group);
        HBox switcher = new HBox(liveToggle, historyToggle);
        switcher.getStyleClass().add("view-switch");
        switcher.setMaxWidth(Region.USE_PREF_SIZE);
        clearHistoryButton.getStyleClass().add("link-button");
        clearHistoryButton.setOnAction(event -> clearHistory());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox header = new HBox(8, switcher, gap, clearHistoryButton);
        header.getStyleClass().add("view-header");
        header.setAlignment(Pos.CENTER_LEFT);
        StackPane stack = new StackPane(log, historyPane);
        VBox.setVgrow(stack, Priority.ALWAYS);
        logPanel = new VBox(header, stack);
        logPanel.setMinHeight(110);
        group.selectedToggleProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) {
                oldValue.setSelected(true);
                return;
            }
            boolean history = newValue == historyToggle;
            log.setVisible(!history);
            log.setManaged(!history);
            historyPane.setVisible(history);
            historyPane.setManaged(history);
            clearHistoryButton.setVisible(history);
            clearHistoryButton.setManaged(history);
            if (history) {
                refreshHistory();
            }
        });
        liveToggle.setSelected(true);
        return logPanel;
    }

    private void refreshHistory() {
        historyPane.show(current == null ? List.of() : savedRuns(current));
    }

    private void openRun(RunRecord run) {
        RunView existing = viewOf(current);
        if (current == null || existing != null && existing.running) {
            return;
        }
        runs.put(current.id(), viewFrom(run));
        update(current);
    }

    private void clearHistory() {
        RunView existing = viewOf(current);
        if (current == null || existing != null && existing.running) {
            return;
        }
        host.runStore().clear(current.id());
        saved.remove(current.id());
        refreshHistory();
    }

    private RunView viewOf(ScenarioItem scenario) {
        return scenario == null ? null : runs.get(scenario.id());
    }

    private StepResult resultOf(String stepId) {
        RunView view = viewOf(current);
        return view == null ? null : view.results.get(stepId);
    }

    private StepCell.Support support() {
        return new StepCell.Support() {
            @Override
            public Optional<RequestItem> request(String requestId) {
                return ScenarioPane.this.request(requestId);
            }

            @Override
            public StepResult.State state(String stepId) {
                RunView view = viewOf(current);
                return view == null ? null : view.states.get(stepId);
            }

            @Override
            public int status(String stepId) {
                StepResult result = resultOf(stepId);
                if (result != null) {
                    return result.status();
                }
                RunStep recorded = recordedOf(stepId);
                return recorded == null ? 0 : recorded.status();
            }

            @Override
            public String detail(String stepId) {
                StepResult result = resultOf(stepId);
                if (result != null) {
                    return StepSummary.describe(result);
                }
                RunStep recorded = recordedOf(stepId);
                return recorded == null ? "" : recorded.detail();
            }

            @Override
            public long millis(String stepId) {
                StepResult result = resultOf(stepId);
                if (result != null) {
                    return result.millis();
                }
                RunStep recorded = recordedOf(stepId);
                return recorded == null ? 0 : recorded.millis();
            }

            @Override
            public void move(String stepId, int index) {
                if (current != null) {
                    commit(ScenarioEdit.withMovedStep(current, stepId, index), true);
                }
            }

            @Override
            public void remove(String stepId) {
                if (current != null) {
                    String neighbour = null;
                    List<Step> before = current.steps();
                    for (int i = 0; i < before.size(); i++) {
                        if (before.get(i).id().equals(stepId)) {
                            int next = i + 1 < before.size() ? i + 1 : i - 1;
                            neighbour = next >= 0 ? before.get(next).id() : null;
                        }
                    }
                    commit(ScenarioEdit.withoutStep(current, stepId), true);
                    if (neighbour != null) {
                        selectById(neighbour);
                    }
                }
            }
        };
    }

    private void startRun() {
        if (current == null) {
            return;
        }
        RunView view = runs.computeIfAbsent(current.id(), key -> new RunView());
        if (view.running) {
            return;
        }
        showLog(true);
        liveToggle.setSelected(true);
        view.results.clear();
        view.states.clear();
        view.recorded.clear();
        view.log.setLength(0);
        view.summary = "";
        view.running = true;
        CancelToken token = new CancelToken();
        view.token = token;
        ScenarioItem scenario = current;
        Project project = host.project();
        String environment = host.environmentId();
        StepSender sender = host.sender();
        RunStore store = host.runStore();
        long startedAt = System.currentTimeMillis();
        String environmentName = project == null ? ""
                : project.findEnvironment(environment).map(Environment::name).orElse("");
        Map<String, String> labels = stepLabels(scenario);
        refreshRunState();
        refreshLog();
        list.refresh();
        diagram.refresh();
        pool.execute(() -> {
            try {
                new ScenarioRunner(sender).run(project, environment, scenario, token,
                        listener(scenario, view, store, startedAt, environmentName, labels));
            } catch (RuntimeException error) {
                Platform.runLater(() -> {
                    view.running = false;
                    view.summary = "Run failed: " + error.getMessage();
                    view.log.append(view.summary).append('\n');
                    update(scenario);
                });
            }
        });
    }

    private ScenarioListener listener(ScenarioItem scenario, RunView view, RunStore store, long startedAt,
                                      String environmentName, Map<String, String> labels) {
        Map<String, Step> byId = new HashMap<>();
        scenario.steps().forEach(step -> byId.put(step.id(), step));
        return new ScenarioListener() {
            @Override
            public void onStarted(List<Step> steps) {
                Platform.runLater(() -> {
                    steps.forEach(step -> view.states.put(step.id(), StepResult.State.PENDING));
                    update(scenario);
                });
            }

            @Override
            public void onStepStarted(Step step) {
                Platform.runLater(() -> {
                    view.states.put(step.id(), StepResult.State.RUNNING);
                    update(scenario);
                });
            }

            @Override
            public void onStepFinished(StepResult result) {
                Platform.runLater(() -> {
                    String id = result.step().id();
                    view.results.put(id, result);
                    view.states.put(id, result.state());
                    RunLog.stepLines(result, RunLog.label(result.step()))
                            .forEach(line -> view.log.append(line).append('\n'));
                    update(scenario);
                });
            }

            @Override
            public void onFinished(ScenarioResult result) {
                List<RunRecord> persisted = persist(store, scenario, environmentName, startedAt, result, labels);
                Platform.runLater(() -> {
                    if (persisted != null) {
                        saved.put(scenario.id(), persisted);
                        if (current != null && current.id().equals(scenario.id())) {
                            refreshHistory();
                        }
                    }
                    view.running = false;
                    view.summary = RunLog.summary(result);
                    view.log.append(view.summary).append('\n');
                    update(scenario);
                });
            }
        };
    }

    private void update(ScenarioItem scenario) {
        if (current == null || !current.id().equals(scenario.id())) {
            return;
        }
        list.refresh();
        diagram.refresh();
        refreshRunState();
        refreshLog();
        Step selected = list.getSelectionModel().getSelectedItem();
        if (selected != null) {
            inspector.showResult(inspectedResult(selected.id()));
        }
    }

    private void refreshRunState() {
        RunView view = viewOf(current);
        boolean running = view != null && view.running;
        run.setDisable(current == null || running || items.isEmpty());
        clearHistoryButton.setDisable(current == null || running);
        stop.setDisable(!running);
        diagram.syncRun(!run.isDisable(), !stop.isDisable());
        ScenarioHistory history = current == null ? null : history();
        undoButton.setDisable(history == null || !history.canUndo());
        redoButton.setDisable(history == null || !history.canRedo());
        refreshSummary();
    }

    private void refreshSummary() {
        RunView view = viewOf(current);
        int count = items.size();
        String steps = count == 0 ? "No steps yet" : count + (count == 1 ? " step" : " steps");
        summary.setText(view != null && view.running ? "Running…" : steps);
    }

    private void refreshLog() {
        RunView view = viewOf(current);
        log.setText(view == null ? "" : view.log.toString());
        log.moveCaret(log.getLength());
    }
}
