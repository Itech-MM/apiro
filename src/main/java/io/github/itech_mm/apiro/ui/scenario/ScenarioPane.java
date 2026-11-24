package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_framework.api_client.utils.CancelToken;
import io.github.itech_mm.apiro.model.OnFailure;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.service.scenario.RunLog;
import io.github.itech_mm.apiro.service.scenario.ScenarioEdit;
import io.github.itech_mm.apiro.service.scenario.ScenarioListener;
import io.github.itech_mm.apiro.service.scenario.ScenarioResult;
import io.github.itech_mm.apiro.service.scenario.ScenarioRunner;
import io.github.itech_mm.apiro.service.scenario.StepResult;
import io.github.itech_mm.apiro.service.scenario.StepSender;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public final class ScenarioPane extends BorderPane {

    public interface Host {

        Project project();

        String environmentId();

        StepSender sender();

        void save(ScenarioItem scenario);
    }

    private static final String STOP = "Stop";
    private static final String CONTINUE = "Continue";

    private static final class RunView {

        final Map<String, StepResult> results = new HashMap<>();
        final Map<String, StepResult.State> states = new HashMap<>();
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
    private final Button run = new Button("Run");
    private final Button stop = new Button("Stop");
    private final ObservableList<Step> items = FXCollections.observableArrayList();
    private final ListView<Step> list = new ListView<>(items);
    private final StepInspector inspector;
    private final TextArea log = new TextArea();
    private ScenarioItem current;
    private boolean loading;
    private boolean syncing;

    public ScenarioPane(Host host) {
        this.host = host;
        getStyleClass().add("scenario-pane");
        inspector = new StepInspector(this::onStepEdited);

        title.getStyleClass().add("scenario-title");
        summary.getStyleClass().add("muted");
        policy.setTooltip(new Tooltip("What happens after a step fails"));
        Label policyLabel = new Label("On failure");
        policyLabel.getStyleClass().add("muted");
        run.getStyleClass().add("scenario-run");
        stop.getStyleClass().add("scenario-stop");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, title, summary, spacer, policyLabel, policy, addStep, run, stop);
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

        log.setEditable(false);
        log.setPrefRowCount(7);
        log.getStyleClass().add("scenario-log");

        list.getStyleClass().add("scenario-list");
        SplitPane horizontal = new SplitPane(list, inspector);
        horizontal.getStyleClass().add("editor-split");
        horizontal.setDividerPositions(0.5);
        SplitPane vertical = new SplitPane(horizontal, log);
        vertical.setOrientation(Orientation.VERTICAL);
        vertical.getStyleClass().add("editor-split");
        vertical.setDividerPositions(0.74);
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
        stop.setOnAction(event -> {
            RunView view = viewOf(current);
            if (view != null && view.token != null) {
                view.token.cancel();
            }
        });
        refreshRunState();
    }

    public void show(ScenarioItem scenario) {
        boolean same = current != null && current.id().equals(scenario.id());
        if (same && current.equals(scenario)) {
            list.refresh();
            return;
        }
        String keep = same ? selectedId() : null;
        current = scenario;
        loading = true;
        title.setText(scenario.name());
        policy.setValue(scenario.onFailure() == OnFailure.CONTINUE ? CONTINUE : STOP);
        loading = false;
        syncing = true;
        items.setAll(scenario.steps());
        selectById(keep);
        syncing = false;
        inspectSelected();
        refreshRunState();
        refreshLog();
    }

    private void onStepEdited(Step step) {
        if (current == null) {
            return;
        }
        ScenarioItem next = ScenarioEdit.withStep(current, step);
        if (next == current) {
            return;
        }
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
        host.save(next);
        refreshSummary();
    }

    private void commit(ScenarioItem next, boolean rebuild) {
        if (next == current) {
            return;
        }
        String keep = selectedId();
        current = next;
        if (rebuild) {
            syncing = true;
            items.setAll(next.steps());
            selectById(keep);
            syncing = false;
            inspectSelected();
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

    private void inspectSelected() {
        Step selected = list.getSelectionModel().getSelectedItem();
        if (selected != null && selected.equals(inspector.step())) {
            return;
        }
        if (selected == null) {
            inspector.show(null, "", null);
            return;
        }
        inspector.show(selected, requestLabel(selected), resultOf(selected.id()));
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
            public long millis(String stepId) {
                StepResult result = resultOf(stepId);
                return result == null ? 0 : result.millis();
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
                    commit(ScenarioEdit.withoutStep(current, stepId), true);
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
        view.results.clear();
        view.states.clear();
        view.log.setLength(0);
        view.summary = "";
        view.running = true;
        CancelToken token = new CancelToken();
        view.token = token;
        ScenarioItem scenario = current;
        Project project = host.project();
        String environment = host.environmentId();
        StepSender sender = host.sender();
        refreshRunState();
        refreshLog();
        list.refresh();
        pool.execute(() -> {
            try {
                new ScenarioRunner(sender).run(project, environment, scenario, token, listener(scenario, view));
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

    private ScenarioListener listener(ScenarioItem scenario, RunView view) {
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
                Platform.runLater(() -> {
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
        refreshRunState();
        refreshLog();
        Step selected = list.getSelectionModel().getSelectedItem();
        if (selected != null) {
            inspector.showResult(resultOf(selected.id()));
        }
    }

    private void refreshRunState() {
        RunView view = viewOf(current);
        boolean running = view != null && view.running;
        run.setDisable(current == null || running || items.isEmpty());
        stop.setDisable(!running);
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
        log.positionCaret(log.getLength());
    }
}
