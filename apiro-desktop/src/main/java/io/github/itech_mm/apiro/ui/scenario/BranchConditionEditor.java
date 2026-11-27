package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_mm.apiro.model.BranchRule;
import io.github.itech_mm.apiro.model.Edge;
import io.github.itech_mm.apiro.model.EdgeCondition;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.ui.editor.VariableField;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

final class BranchConditionEditor extends VBox {

    private final Consumer<Edge> onChange;
    private final Consumer<String> onRemove;
    private final Consumer<Integer> onMove;
    private final Runnable onBack;
    private final Button backButton = new Button("← Back to step");
    private final Label fromLabel = new Label();
    private final ComboBox<Step> targetBox = new ComboBox<>();
    private final TextField labelField = new TextField();
    private final ComboBox<EdgeCondition.MatchMode> modeBox = new ComboBox<>(
            FXCollections.observableArrayList(EdgeCondition.MatchMode.values()));
    private final MenuButton presetButton = new MenuButton("Preset");
    private final Button upButton = new Button("▲");
    private final Button downButton = new Button("▼");
    private final Button removeButton = new Button("Remove");
    private final Label warningLabel = new Label();
    private final VBox rulesSection = new VBox(8);
    private final VBox rows = new VBox(8);
    private final List<RuleRow> list = new ArrayList<>();
    private List<Step> scenarioSteps = List.of();
    private Set<String> blockedTargets = Set.of();
    private Edge edge;
    private boolean loading;

    BranchConditionEditor(Consumer<Edge> onChange, Consumer<String> onRemove, Consumer<Integer> onMove,
                          Runnable onBack) {
        super(10);
        this.onChange = onChange;
        this.onRemove = onRemove;
        this.onMove = onMove;
        this.onBack = onBack;
        getStyleClass().add("section-body");

        backButton.getStyleClass().add("link-button");
        backButton.setOnAction(event -> this.onBack.run());

        fromLabel.getStyleClass().add("muted");
        targetBox.getStyleClass().add("field-combo");
        targetBox.setMaxWidth(Double.MAX_VALUE);
        targetBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Step step) {
                return step == null ? "" : stepLabel(step) + (blockedTargets.contains(step.id()) ? " (loop)" : "");
            }

            @Override
            public Step fromString(String string) {
                return null;
            }
        });

        labelField.getStyleClass().add("kv-field");
        labelField.setPromptText("Optional label (e.g. admin, retry)");

        modeBox.getStyleClass().add("field-combo");
        modeBox.setMaxWidth(Double.MAX_VALUE);
        modeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(EdgeCondition.MatchMode mode) {
                if (mode == null) {
                    return "";
                }
                return switch (mode) {
                    case ALWAYS -> "Always";
                    case ALL -> "Match ALL rules (AND)";
                    case ANY -> "Match ANY rule (OR)";
                    case ELSE -> "Else (fallback)";
                };
            }

            @Override
            public EdgeCondition.MatchMode fromString(String string) {
                return null;
            }
        });

        setupPresets();

        upButton.getStyleClass().addAll("icon-button", "icon-button-sm");
        downButton.getStyleClass().addAll("icon-button", "icon-button-sm");
        removeButton.getStyleClass().add("link-button");
        upButton.setOnAction(event -> this.onMove.accept(-1));
        downButton.setOnAction(event -> this.onMove.accept(1));
        removeButton.setOnAction(event -> {
            if (edge != null) {
                this.onRemove.accept(edge.id());
            }
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox priorityBar = new HBox(6, upButton, downButton, presetButton, spacer, removeButton);
        priorityBar.setAlignment(Pos.CENTER_LEFT);

        warningLabel.getStyleClass().add("override-error");
        warningLabel.setWrapText(true);
        warningLabel.setVisible(false);
        warningLabel.setManaged(false);

        Label rulesTitle = new Label("Rules");
        rulesTitle.getStyleClass().add("section-title");
        Button addRule = new Button("Add rule");
        addRule.getStyleClass().add("link-button");
        addRule.setOnAction(event -> {
            addRow(BranchRule.status(BranchRule.Operator.EQUALS, "200"));
            emit();
        });
        rulesSection.getChildren().addAll(rulesTitle, rows, addRule);

        getChildren().addAll(
                backButton,
                fromLabel,
                labeled("Target step", targetBox),
                labeled("Label", labelField),
                labeled("Condition mode", modeBox),
                priorityBar,
                warningLabel,
                rulesSection);

        targetBox.valueProperty().addListener((obs, oldValue, newValue) -> emit());
        labelField.textProperty().addListener((obs, oldValue, newValue) -> emit());
        modeBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!loading && (newValue == EdgeCondition.MatchMode.ALL || newValue == EdgeCondition.MatchMode.ANY)
                    && list.isEmpty()) {
                addRow(BranchRule.status(BranchRule.Operator.EQUALS, "200"));
            }
            syncRulesVisibility();
            emit();
        });
    }

    private void setupPresets() {
        MenuItem s200 = new MenuItem("Status = 200");
        s200.setOnAction(e -> applyPreset(EdgeCondition.MatchMode.ALL,
                BranchRule.status(BranchRule.Operator.EQUALS, "200")));
        MenuItem s404 = new MenuItem("Status = 404");
        s404.setOnAction(e -> applyPreset(EdgeCondition.MatchMode.ALL,
                BranchRule.status(BranchRule.Operator.EQUALS, "404")));
        MenuItem jsonEq = new MenuItem("JSON path equals...");
        jsonEq.setOnAction(e -> applyPreset(EdgeCondition.MatchMode.ALL,
                BranchRule.jsonPath("$.status", BranchRule.Operator.EQUALS, "READY")));
        MenuItem varEq = new MenuItem("Variable equals...");
        varEq.setOnAction(e -> applyPreset(EdgeCondition.MatchMode.ALL,
                BranchRule.variable("role", BranchRule.Operator.EQUALS, "admin")));
        MenuItem failedRecovery = new MenuItem("Step failed (recovery)");
        failedRecovery.setOnAction(e -> applyPreset(EdgeCondition.MatchMode.ALL,
                BranchRule.stepFailed()));
        presetButton.getItems().addAll(s200, s404, jsonEq, varEq, failedRecovery);
    }

    private void applyPreset(EdgeCondition.MatchMode mode, BranchRule rule) {
        if (edge == null) {
            return;
        }
        loading = true;
        modeBox.setValue(mode);
        rows.getChildren().clear();
        list.clear();
        addRow(rule);
        syncRulesVisibility();
        loading = false;
        emit();
    }

    void set(Edge nextEdge, List<Step> steps, List<String> unresolvedVars, Set<String> blocked, boolean canMoveUp,
             boolean canMoveDown) {
        loading = true;
        this.edge = nextEdge;
        this.scenarioSteps = steps == null ? List.of() : List.copyOf(steps);
        this.blockedTargets = blocked == null ? Set.of() : Set.copyOf(blocked);
        if (nextEdge == null) {
            rows.getChildren().clear();
            list.clear();
            loading = false;
            return;
        }
        Step fromStep = findStep(nextEdge.from());
        fromLabel.setText("From: " + (fromStep == null ? nextEdge.from() : stepLabel(fromStep)));
        List<Step> targets = scenarioSteps.stream()
                .filter(s -> !s.id().equals(nextEdge.from())
                        && (!blockedTargets.contains(s.id()) || s.id().equals(nextEdge.to())))
                .toList();
        targetBox.setItems(FXCollections.observableArrayList(targets));
        targetBox.setValue(findStep(nextEdge.to()));
        labelField.setText(nextEdge.label());
        modeBox.setValue(nextEdge.condition().mode());
        upButton.setDisable(!canMoveUp);
        downButton.setDisable(!canMoveDown);

        rows.getChildren().clear();
        list.clear();
        nextEdge.condition().rules().forEach(this::addRow);

        if (unresolvedVars == null || unresolvedVars.isEmpty()) {
            warningLabel.setText("");
            warningLabel.setVisible(false);
            warningLabel.setManaged(false);
        } else {
            warningLabel.setText("Undefined variable(s): " + String.join(", ", unresolvedVars));
            warningLabel.setVisible(true);
            warningLabel.setManaged(true);
        }
        syncRulesVisibility();
        loading = false;
    }

    private void syncRulesVisibility() {
        EdgeCondition.MatchMode mode = modeBox.getValue();
        boolean show = mode == EdgeCondition.MatchMode.ALL || mode == EdgeCondition.MatchMode.ANY;
        rulesSection.setVisible(show);
        rulesSection.setManaged(show);
    }

    private void addRow(BranchRule rule) {
        RuleRow row = new RuleRow(rule);
        list.add(row);
        rows.getChildren().add(row.box);
    }

    private void emit() {
        if (loading || edge == null) {
            return;
        }
        Step target = targetBox.getValue();
        String toId = target != null ? target.id() : edge.to();
        EdgeCondition.MatchMode mode = modeBox.getValue() == null ? EdgeCondition.MatchMode.ALWAYS : modeBox.getValue();
        List<BranchRule> rules = (mode == EdgeCondition.MatchMode.ALL || mode == EdgeCondition.MatchMode.ANY)
                ? list.stream().map(RuleRow::value).toList()
                : List.of();
        Edge updated = new Edge(edge.id(), edge.from(), toId, new EdgeCondition(mode, rules), labelField.getText());
        edge = updated;
        onChange.accept(updated);
    }

    private Step findStep(String id) {
        for (Step step : scenarioSteps) {
            if (step.id().equals(id)) {
                return step;
            }
        }
        return null;
    }

    private String stepLabel(Step step) {
        int idx = scenarioSteps.indexOf(step);
        String name = step.name().isBlank() ? step.requestId() : step.name();
        return idx >= 0 ? (idx + 1) + ". " + name : name;
    }

    private static VBox labeled(String text, Node field) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        return new VBox(4, label, field);
    }

    private final class RuleRow {

        final ComboBox<BranchRule.Source> source = new ComboBox<>(
                FXCollections.observableArrayList(BranchRule.Source.values()));
        final TextField expression = new TextField();
        final ComboBox<BranchRule.Operator> operator = new ComboBox<>(
                FXCollections.observableArrayList(BranchRule.Operator.values()));
        final VariableField expected = new VariableField();
        final Button remove = new Button("×");
        final VBox box;

        RuleRow(BranchRule rule) {
            source.setValue(rule.source());
            expression.setText(rule.expression());
            operator.setValue(rule.operator());
            expected.setText(rule.expected());
            expression.getStyleClass().add("kv-field");
            expected.getStyleClass().add("kv-field");
            source.getStyleClass().add("field-combo");
            operator.getStyleClass().add("field-combo");
            source.setPrefWidth(140);
            source.setMinWidth(Region.USE_PREF_SIZE);
            operator.setPrefWidth(140);
            operator.setMinWidth(Region.USE_PREF_SIZE);
            remove.getStyleClass().addAll("icon-button", "icon-button-sm");
            HBox.setHgrow(expression, Priority.ALWAYS);
            HBox.setHgrow(expected, Priority.ALWAYS);

            HBox first = new HBox(4, source, expression, remove);
            Region gap = new Region();
            gap.setMinWidth(26);
            gap.setPrefWidth(26);
            gap.setMaxWidth(26);
            HBox second = new HBox(4, operator, expected, gap);
            first.setAlignment(Pos.CENTER_LEFT);
            second.setAlignment(Pos.CENTER_LEFT);
            box = new VBox(4, first, second);
            box.getStyleClass().add("assertion-row");
            syncEnabled();

            source.valueProperty().addListener((obs, oldValue, newValue) -> {
                syncEnabled();
                emit();
            });
            operator.valueProperty().addListener((obs, oldValue, newValue) -> {
                syncEnabled();
                emit();
            });
            expression.textProperty().addListener((obs, oldValue, newValue) -> emit());
            expected.valueProperty().addListener((obs, oldValue, newValue) -> emit());
            remove.setOnAction(event -> {
                list.remove(this);
                rows.getChildren().remove(box);
                emit();
            });
        }

        private void syncEnabled() {
            BranchRule.Source current = source.getValue();
            boolean needsExpr = current == BranchRule.Source.JSON_PATH
                    || current == BranchRule.Source.HEADER
                    || current == BranchRule.Source.VARIABLE;
            expression.setDisable(!needsExpr);
            if (current == BranchRule.Source.JSON_PATH) {
                expression.setPromptText("$.field.path");
            } else if (current == BranchRule.Source.HEADER) {
                expression.setPromptText("Header name");
            } else if (current == BranchRule.Source.VARIABLE) {
                expression.setPromptText("Variable name");
            } else {
                expression.setPromptText("");
            }

            BranchRule.Operator op = operator.getValue();
            boolean needsExp = op != BranchRule.Operator.EXISTS
                    && op != BranchRule.Operator.NOT_EXISTS
                    && op != BranchRule.Operator.IS_EMPTY
                    && op != BranchRule.Operator.NOT_EMPTY;
            expected.setDisable(!needsExp);
        }

        BranchRule value() {
            return new BranchRule(source.getValue(), expression.getText(), operator.getValue(), expected.getText());
        }
    }
}
