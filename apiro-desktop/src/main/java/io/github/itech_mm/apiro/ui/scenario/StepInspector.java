package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_framework.code_editor.CodeEditor;
import io.github.itech_mm.apiro.model.Assertion;
import io.github.itech_mm.apiro.model.BodyOverride;
import io.github.itech_mm.apiro.model.Edge;
import io.github.itech_mm.apiro.model.Extractor;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.OnFailure;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.service.scenario.Assertions;
import io.github.itech_mm.apiro.service.scenario.Extractors;
import io.github.itech_mm.apiro.service.scenario.ScenarioEdit;
import io.github.itech_mm.apiro.service.scenario.ScenarioGraph;
import io.github.itech_mm.apiro.service.scenario.StepResult;
import io.github.itech_mm.apiro.service.scenario.VariableUsage;
import io.github.itech_mm.apiro.ui.EditorStyle;
import io.github.itech_mm.apiro.ui.JsonPretty;
import io.github.itech_mm.apiro.ui.editor.VariableField;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

final class StepInspector extends StackPane {

    interface Callbacks {

        void onStepChanged(Step step);

        void onAddBranch(String fromStepId, String toStepId);

        void onUpdateEdge(Edge edge);

        void onRemoveEdge(String edgeId);

        void onMoveEdge(String fromStepId, String edgeId, int targetRelativeIndex);

        void onSelectEdge(String edgeId);

        void onSelectStep(String stepId);
    }

    private static final int BODY_LIMIT = 100000;
    private static final String DEFAULT_POLICY = "Scenario default";

    private List<Extractor> extractorValues = List.of();
    private List<Assertion> assertionValues = List.of();
    private List<KeyValue> headerValues = List.of();
    private List<KeyValue> queryValues = List.of();
    private String urlOverrideValue = "";
    private BodyOverride bodyValue;
    private final Callbacks callbacks;
    private final Label placeholder = new Label("Select a step or branch to edit it");
    private final Label request = new Label();
    private final TextField name = new TextField();
    private final TextField delay = new TextField();
    private final ComboBox<String> policy = new ComboBox<>(
            FXCollections.observableArrayList(DEFAULT_POLICY, "Stop", "Continue"));
    private final VariableField urlOverride = new VariableField();
    private final Button copyUrlButton = new Button("Copy from request");
    private final Button clearUrlButton = new Button("Use request URL");
    private final QueryEditor queryParams = new QueryEditor(list -> {
        queryValues = list;
        emit();
    });
    private final HeaderEditor headers = new HeaderEditor(list -> {
        headerValues = list;
        emit();
    });
    private final BodyOverrideEditor bodyEditor = new BodyOverrideEditor(override -> {
        bodyValue = override;
        emit();
    });
    private final ExtractorEditor extractors = new ExtractorEditor(list -> {
        extractorValues = list;
        emit();
    });
    private final AssertionEditor assertions = new AssertionEditor(list -> {
        assertionValues = list;
        emit();
    });
    private final VBox branchRows = new VBox(6);
    private final MenuButton addBranchButton = new MenuButton("Add branch");
    private final Label status = new Label();
    private final Label logDetails = new Label();
    private final CodeEditor body = new CodeEditor();
    private final Label overridesTabLabel = new Label("Overrides");
    private final Label extractAssertTabLabel = new Label("Extract & Assert");
    private final Label branchesTabLabel = new Label("Branches");
    private final Label logsTabLabel = new Label("Logs");
    private final Tab logsTab = tab(logsTabLabel);
    private final Tab generalTab = tab(new Label("General"));
    private final TabPane mainTabs;
    private final BranchConditionEditor edgeEditor;
    private final ScrollPane edgeScroll;
    private Step step;
    private Edge selectedEdge;
    private ScenarioItem scenario;
    private RequestItem ownerRequest;
    private VariableUsage.Result usage = VariableUsage.Result.EMPTY;
    private boolean loading;

    StepInspector(Callbacks callbacks) {
        this.callbacks = callbacks;
        getStyleClass().add("step-inspector");
        setMinWidth(340);

        request.getStyleClass().add("muted");
        name.setPromptText("Step name (defaults to the request name)");
        delay.setPromptText("Delay before the step, in ms");
        delay.setTooltip(new Tooltip("Delay before running this step, in milliseconds"));
        name.getStyleClass().add("kv-field");
        delay.getStyleClass().add("kv-field");
        policy.getStyleClass().add("field-combo");
        policy.setMaxWidth(Double.MAX_VALUE);

        VBox general = new VBox(10, request, labeled("Name", name), labeled("Delay (ms)", delay),
                labeled("On failure", policy));
        general.getStyleClass().add("section-body");
        VBox generalPane = new VBox(general);
        generalPane.setFillWidth(true);
        generalTab.setContent(scrollPane(generalPane));

        urlOverride.getStyleClass().add("kv-field");
        copyUrlButton.getStyleClass().add("link-button");
        clearUrlButton.getStyleClass().add("link-button");
        copyUrlButton.setOnAction(event -> {
            if (ownerRequest != null) {
                urlOverride.setText(ownerRequest.url());
            }
        });
        clearUrlButton.setOnAction(event -> urlOverride.setText(""));
        HBox urlActions = new HBox(10, copyUrlButton, clearUrlButton);
        urlActions.setAlignment(Pos.CENTER_LEFT);
        HBox urlHeader = InfoTip.header("URL / Path override",
                "Optional. Replaces the request URL or path for this step only.");
        VBox urlSection = new VBox(6, urlHeader, urlOverride, urlActions);
        urlSection.getStyleClass().add("section-body");

        queryParams.getStyleClass().add("section-body");
        headers.getStyleClass().add("section-body");
        extractors.getStyleClass().add("section-body");
        assertions.getStyleClass().add("section-body");

        HBox branchesHeader = InfoTip.header("Outgoing branches",
                "Evaluated top-to-bottom after this step runs. Without branches, steps run in linear order.");
        addBranchButton.getStyleClass().add("link-button");
        addBranchButton.setOnShowing(event -> populateAddBranchMenu());
        VBox branchesSection = new VBox(8, branchesHeader, branchRows, addBranchButton);
        branchesSection.getStyleClass().add("section-body");

        VBox overridesPane = new VBox(urlSection, queryParams, headers, bodyEditor);
        overridesPane.setFillWidth(true);
        Tab overridesTab = tab(overridesTabLabel);
        overridesTab.setContent(scrollPane(overridesPane));

        VBox extractAssertPane = new VBox(extractors, assertions);
        extractAssertPane.setFillWidth(true);
        Tab extractAssertTab = tab(extractAssertTabLabel);
        extractAssertTab.setContent(scrollPane(extractAssertPane));

        VBox branchesPane = new VBox(branchesSection);
        branchesPane.setFillWidth(true);
        Tab branchesTab = tab(branchesTabLabel);
        branchesTab.setContent(scrollPane(branchesPane));

        EditorStyle.applyInset(body);
        body.setEditable(false);
        body.setShowLineNumbers(false);
        body.setMinHeight(180);
        body.setPrefHeight(260);
        body.setMaxHeight(Double.MAX_VALUE);
        body.getStyleClass().add("step-response-body");
        status.getStyleClass().add("muted");
        logDetails.getStyleClass().add("step-log-details");
        logDetails.setWrapText(true);
        logDetails.setVisible(false);
        logDetails.setManaged(false);
        Label responseTitle = new Label("Last response");
        responseTitle.getStyleClass().add("section-title");
        Region logsSpacer = new Region();
        HBox.setHgrow(logsSpacer, Priority.ALWAYS);
        HBox logsHeaderRow = new HBox(8, responseTitle, logsSpacer, status);
        logsHeaderRow.setAlignment(Pos.CENTER_LEFT);
        VBox logsHead = new VBox(6, logsHeaderRow, logDetails);
        logsHead.getStyleClass().add("section-body");
        VBox bodyWrap = new VBox(body);
        bodyWrap.setPadding(new Insets(10, 16, 12, 16));
        VBox.setVgrow(body, Priority.ALWAYS);
        VBox.setVgrow(bodyWrap, Priority.ALWAYS);
        VBox logsRoot = new VBox(logsHead, bodyWrap);
        logsRoot.setFillWidth(true);
        logsTab.setContent(logsRoot);

        mainTabs = tabPane(generalTab, logsTab, overridesTab, extractAssertTab, branchesTab);
        mainTabs.getStyleClass().add("inspector-subtabs");

        edgeEditor = new BranchConditionEditor(
                updated -> {
                    selectedEdge = updated;
                    callbacks.onUpdateEdge(updated);
                },
                callbacks::onRemoveEdge,
                delta -> moveSelectedEdge(delta),
                () -> {
                    if (selectedEdge != null) {
                        callbacks.onSelectStep(selectedEdge.from());
                    }
                });
        edgeScroll = scrollPane(edgeEditor);

        placeholder.getStyleClass().add("muted");
        placeholder.setPadding(new Insets(16));
        setView(placeholder);

        name.textProperty().addListener((obs, oldValue, newValue) -> emit());
        delay.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                delay.setText(oldValue);
                return;
            }
            emit();
        });
        policy.valueProperty().addListener((obs, oldValue, newValue) -> emit());
        urlOverride.valueProperty().addListener((obs, oldValue, newValue) -> {
            updateUrlButtons();
            emit();
        });
    }

    private static Tab tab(Label graphic) {
        Tab tab = new Tab();
        tab.setGraphic(graphic);
        tab.setClosable(false);
        return tab;
    }

    private static TabPane tabPane(Tab... items) {
        TabPane pane = new TabPane(items);
        pane.getStyleClass().add("request-tabs");
        pane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return pane;
    }

    private static ScrollPane scrollPane(Node content) {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        pane.getStyleClass().add("inspector-scroll");
        return pane;
    }

    private static VBox labeled(String text, Node field) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        return new VBox(4, label, field);
    }

    private void setView(Node view) {
        getChildren().setAll(view);
        StackPane.setAlignment(view, view == placeholder ? Pos.CENTER : Pos.TOP_LEFT);
    }

    Step step() {
        return step;
    }

    Edge edge() {
        return selectedEdge;
    }

    void focusName() {
        if (step != null && selectedEdge == null) {
            if (mainTabs.getSelectionModel().getSelectedItem() == generalTab) {
                name.requestFocus();
                name.selectAll();
            } else {
                mainTabs.requestFocus();
            }
        }
    }

    void show(Step next, String requestLabel, RequestItem owner, StepResult result, ScenarioItem nextScenario,
              VariableUsage.Result nextUsage) {
        loading = true;
        selectedEdge = null;
        step = next;
        scenario = nextScenario;
        usage = nextUsage == null ? VariableUsage.Result.EMPTY : nextUsage;
        if (next == null) {
            setView(placeholder);
            loading = false;
            return;
        }
        request.setText("Request: " + requestLabel);
        name.setText(next.name());
        delay.setText(next.delayMillis() == 0 ? "" : String.valueOf(next.delayMillis()));
        policy.setValue(next.onFailure() == null ? DEFAULT_POLICY
                : next.onFailure() == OnFailure.STOP ? "Stop" : "Continue");
        ownerRequest = owner;
        urlOverrideValue = next.urlOverride();
        urlOverride.setText(next.urlOverride());
        updateUrlButtons();
        queryValues = next.query();
        headerValues = next.headers();
        extractorValues = next.extractors();
        assertionValues = next.assertions();
        queryParams.set(next.query(), owner);
        headers.set(next.headers());
        bodyValue = next.bodyOverride();
        bodyEditor.set(next.bodyOverride(), owner);
        extractors.set(next.extractors());
        assertions.set(next.assertions());
        refreshBranches();
        updateTabCounts();
        setView(mainTabs);
        loading = false;
        showResult(result);
    }

    void showEdge(Edge edge, ScenarioItem nextScenario, VariableUsage.Result nextUsage) {
        loading = true;
        step = null;
        selectedEdge = edge;
        scenario = nextScenario;
        usage = nextUsage == null ? VariableUsage.Result.EMPTY : nextUsage;
        if (edge == null || scenario == null) {
            setView(placeholder);
            loading = false;
            return;
        }
        List<Edge> outgoing = outgoingOf(edge.from());
        int index = indexOfEdge(outgoing, edge.id());
        boolean canUp = index > 0;
        boolean canDown = index >= 0 && index < outgoing.size() - 1;
        List<String> missing = usage.unresolvedForEdge(edge.id());
        Set<String> blocked = ScenarioGraph.bestEffortAncestors(ScenarioEdit.withMaterializedEdges(scenario), edge.from());
        edgeEditor.set(edge, scenario.steps(), missing, blocked, canUp, canDown);
        setView(edgeScroll);
        loading = false;
    }

    void showResult(StepResult result) {
        if (result == null) {
            status.setText("Not run yet");
            logsTabLabel.setText("Logs");
            updateLogDetails("");
            showBody("plaintext", "");
            return;
        }
        String text = result.state().name().toLowerCase();
        if (result.status() > 0) {
            text += "  " + result.status();
        }
        if (result.millis() > 0) {
            text += "  " + result.millis() + " ms";
        }
        if (!result.message().isBlank()) {
            text += "  " + result.message();
        }
        status.setText(text);
        if (result.state() == StepResult.State.PENDING) {
            logsTabLabel.setText("Logs");
        } else if (result.status() > 0) {
            logsTabLabel.setText("Logs (" + result.status() + ")");
        } else {
            logsTabLabel.setText("Logs (" + result.state().name().toLowerCase() + ")");
        }
        updateLogDetails(buildDetailsSummary(result));
        String payload = result.exchange() == null ? "" : result.exchange().response().body();
        if (payload.length() > BODY_LIMIT) {
            int end = Character.isHighSurrogate(payload.charAt(BODY_LIMIT - 1)) ? BODY_LIMIT - 1 : BODY_LIMIT;
            showBody("plaintext", payload.substring(0, end) + "\n…");
        } else if (looksLikeJson(payload)) {
            showBody("json", JsonPretty.format(payload).orElse(payload));
        } else {
            showBody("plaintext", payload);
        }
    }

    private void updateLogDetails(String details) {
        boolean hasDetails = details != null && !details.isBlank();
        logDetails.setText(hasDetails ? details : "");
        logDetails.setVisible(hasDetails);
        logDetails.setManaged(hasDetails);
    }

    private static String buildDetailsSummary(StepResult result) {
        List<String> lines = new ArrayList<>();
        if (!result.extractions().isEmpty()) {
            List<String> items = new ArrayList<>();
            for (Extractors.Result extraction : result.extractions()) {
                String var = extraction.extractor().variable();
                if (extraction.success()) {
                    items.add(var + " = " + extraction.value());
                } else {
                    items.add(var + " (" + extraction.error() + ")");
                }
            }
            lines.add("Extracted: " + String.join(", ", items));
        }
        if (!result.assertions().isEmpty()) {
            long passed = result.assertions().stream().filter(Assertions.Result::passed).count();
            StringBuilder sb = new StringBuilder("Assertions: ")
                    .append(passed).append("/").append(result.assertions().size()).append(" passed");
            for (Assertions.Result assertion : result.assertions()) {
                if (!assertion.passed() && !assertion.message().isBlank()) {
                    sb.append("\n  fail: ").append(assertion.message());
                }
            }
            lines.add(sb.toString());
        }
        if (result.branch() != null && !result.branch().reason().isBlank()) {
            if (result.branch().taken()) {
                lines.add("Branch: -> " + result.branch().targetName() + " (" + result.branch().reason() + ")");
            } else {
                lines.add("Branch: " + result.branch().reason());
            }
        }
        return String.join("\n", lines);
    }

    private void updateTabCounts() {
        int overrideCount = (urlOverrideValue.isBlank() ? 0 : 1)
                + queryValues.size()
                + headerValues.size()
                + (bodyValue == null ? 0 : 1);
        overridesTabLabel.setText(overrideCount > 0 ? "Overrides (" + overrideCount + ")" : "Overrides");
        int eaCount = extractorValues.size() + assertionValues.size();
        extractAssertTabLabel.setText(eaCount > 0 ? "Extract & Assert (" + eaCount + ")" : "Extract & Assert");
        int branchCount = step == null ? 0 : outgoingOf(step.id()).size();
        branchesTabLabel.setText(branchCount > 0 ? "Branches (" + branchCount + ")" : "Branches");
    }

    private void refreshBranches() {
        branchRows.getChildren().clear();
        if (step == null || scenario == null) {
            return;
        }
        List<Edge> outgoing = outgoingOf(step.id());
        if (outgoing.isEmpty()) {
            Label none = new Label("No outgoing branches");
            none.getStyleClass().add("muted");
            branchRows.getChildren().add(none);
            return;
        }
        for (int i = 0; i < outgoing.size(); i++) {
            Edge edge = outgoing.get(i);
            int idx = i;
            String targetName = stepName(edge.to());
            String summaryText = edge.displayLabel().isBlank() ? "always" : edge.displayLabel();
            Label title = new Label("#" + (i + 1) + " -> " + targetName);
            title.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(title, Priority.ALWAYS);
            Label desc = new Label(summaryText);
            desc.getStyleClass().add("muted");
            desc.setWrapText(true);

            Button up = new Button("▲");
            up.getStyleClass().addAll("icon-button", "icon-button-sm");
            up.setDisable(i == 0);
            up.setOnAction(e -> callbacks.onMoveEdge(step.id(), edge.id(), idx - 1));

            Button down = new Button("▼");
            down.getStyleClass().addAll("icon-button", "icon-button-sm");
            down.setDisable(i == outgoing.size() - 1);
            down.setOnAction(e -> callbacks.onMoveEdge(step.id(), edge.id(), idx + 1));

            Button edit = new Button("Edit");
            edit.getStyleClass().add("link-button");
            edit.setOnAction(e -> callbacks.onSelectEdge(edge.id()));

            Button remove = new Button("×");
            remove.getStyleClass().addAll("icon-button", "icon-button-sm");
            remove.setOnAction(e -> callbacks.onRemoveEdge(edge.id()));

            HBox top = new HBox(4, title, up, down, edit, remove);
            top.setAlignment(Pos.CENTER_LEFT);
            VBox row = new VBox(3, top, desc);
            row.getStyleClass().add("branch-list-row");
            if (!usage.unresolvedForEdge(edge.id()).isEmpty()) {
                Label warn = new Label("Undefined var: " + String.join(", ", usage.unresolvedForEdge(edge.id())));
                warn.getStyleClass().add("override-error");
                row.getChildren().add(warn);
            }
            branchRows.getChildren().add(row);
        }
    }

    private void populateAddBranchMenu() {
        addBranchButton.getItems().clear();
        if (step == null || scenario == null) {
            return;
        }
        Set<String> blocked = ScenarioGraph.bestEffortAncestors(ScenarioEdit.withMaterializedEdges(scenario), step.id());
        List<Step> all = scenario.steps();
        for (int i = 0; i < all.size(); i++) {
            Step target = all.get(i);
            if (!target.id().equals(step.id())) {
                boolean allowed = !blocked.contains(target.id());
                String label = (i + 1) + ". " + (target.name().isBlank() ? target.requestId() : target.name())
                        + (allowed ? "" : " (loop)");
                MenuItem item = new MenuItem(label);
                item.setDisable(!allowed);
                item.setOnAction(e -> callbacks.onAddBranch(step.id(), target.id()));
                addBranchButton.getItems().add(item);
            }
        }
        addBranchButton.setDisable(addBranchButton.getItems().isEmpty());
    }

    private void moveSelectedEdge(int delta) {
        if (selectedEdge == null || scenario == null) {
            return;
        }
        List<Edge> outgoing = outgoingOf(selectedEdge.from());
        int idx = indexOfEdge(outgoing, selectedEdge.id());
        if (idx >= 0) {
            callbacks.onMoveEdge(selectedEdge.from(), selectedEdge.id(), idx + delta);
        }
    }

    private List<Edge> outgoingOf(String stepId) {
        List<Edge> out = new ArrayList<>();
        if (scenario == null) {
            return out;
        }
        for (Edge edge : scenario.edges()) {
            if (edge.from().equals(stepId)) {
                out.add(edge);
            }
        }
        return out;
    }

    private static int indexOfEdge(List<Edge> list, String edgeId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(edgeId)) {
                return i;
            }
        }
        return -1;
    }

    private String stepName(String stepId) {
        if (scenario == null) {
            return stepId;
        }
        for (int i = 0; i < scenario.steps().size(); i++) {
            Step candidate = scenario.steps().get(i);
            if (candidate.id().equals(stepId)) {
                String label = candidate.name().isBlank() ? candidate.requestId() : candidate.name();
                return (i + 1) + ". " + label;
            }
        }
        return stepId;
    }

    private void updateUrlButtons() {
        copyUrlButton.setDisable(ownerRequest == null);
        clearUrlButton.setDisable(urlOverride.getText().isBlank());
    }

    private void showBody(String language, String text) {
        body.setLanguage(language);
        body.setText(text);
    }

    private static boolean looksLikeJson(String text) {
        String trimmed = text.stripLeading();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private void emit() {
        if (loading || step == null) {
            return;
        }
        int delayValue = 0;
        try {
            delayValue = delay.getText().isBlank() ? 0 : Integer.parseInt(delay.getText());
        } catch (NumberFormatException ignored) {
            delayValue = 0;
        }
        String chosen = policy.getValue();
        OnFailure onFailure = DEFAULT_POLICY.equals(chosen) || chosen == null ? null
                : "Stop".equals(chosen) ? OnFailure.STOP : OnFailure.CONTINUE;
        urlOverrideValue = urlOverride.getText().trim();
        Step next = new Step(step.id(), step.requestId(), name.getText(), extractorValues, assertionValues,
                headerValues, queryValues, urlOverrideValue, delayValue, onFailure, step.x(), step.y(), bodyValue);
        step = next;
        updateTabCounts();
        callbacks.onStepChanged(next);
    }
}
