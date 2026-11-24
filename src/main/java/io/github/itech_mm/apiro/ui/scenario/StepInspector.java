package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_framework.code_editor.CodeEditor;
import io.github.itech_mm.apiro.model.Assertion;
import io.github.itech_mm.apiro.model.Extractor;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.OnFailure;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.service.scenario.StepResult;
import io.github.itech_mm.apiro.ui.EditorStyle;
import io.github.itech_mm.apiro.ui.JsonPretty;
import java.util.List;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

final class StepInspector extends ScrollPane {

    private static final int BODY_LIMIT = 100000;
    private static final String DEFAULT_POLICY = "Scenario default";

    private List<Extractor> extractorValues = List.of();
    private List<Assertion> assertionValues = List.of();
    private List<KeyValue> headerValues = List.of();
    private final Consumer<Step> onChange;
    private final Label placeholder = new Label("Select a step to edit it");
    private final Label request = new Label();
    private final TextField name = new TextField();
    private final TextField delay = new TextField();
    private final ComboBox<String> policy = new ComboBox<>(
            FXCollections.observableArrayList(DEFAULT_POLICY, "Stop", "Continue"));
    private final HeaderEditor headers = new HeaderEditor(list -> {
        headerValues = list;
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
    private final Label status = new Label();
    private final CodeEditor body = new CodeEditor();
    private final VBox content = new VBox(12);
    private Step step;
    private boolean loading;

    StepInspector(Consumer<Step> onChange) {
        this.onChange = onChange;
        getStyleClass().add("step-inspector");
        setFitToWidth(true);
        setMinWidth(340);
        request.getStyleClass().add("muted");
        name.setPromptText("Step name (defaults to the request name)");
        delay.setPromptText("Delay before the step, in ms");
        EditorStyle.apply(body);
        body.setEditable(false);
        body.setShowLineNumbers(false);
        body.setMinHeight(260);
        body.setPrefHeight(260);
        body.getStyleClass().add("step-response-body");
        status.getStyleClass().add("muted");
        Label responseTitle = new Label("Last response");
        responseTitle.getStyleClass().add("section-title");
        name.getStyleClass().add("kv-field");
        delay.getStyleClass().add("kv-field");
        policy.getStyleClass().add("field-combo");
        policy.setMaxWidth(Double.MAX_VALUE);
        VBox general = new VBox(10, request, labeled("Name", name), labeled("Delay (ms)", delay),
                labeled("On failure", policy));
        general.getStyleClass().add("section-body");
        headers.getStyleClass().add("section-body");
        extractors.getStyleClass().add("section-body");
        assertions.getStyleClass().add("section-body");
        VBox response = new VBox(8, responseTitle, status, body);
        response.getStyleClass().add("section-body");
        content.getChildren().addAll(general, headers, extractors, assertions, response);
        content.setPadding(Insets.EMPTY);
        placeholder.getStyleClass().add("muted");
        placeholder.setPadding(new Insets(16));
        setContent(placeholder);

        name.textProperty().addListener((obs, oldValue, newValue) -> emit());
        delay.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                delay.setText(oldValue);
                return;
            }
            emit();
        });
        policy.valueProperty().addListener((obs, oldValue, newValue) -> emit());
    }

    private static VBox labeled(String text, Node field) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        return new VBox(4, label, field);
    }

    Step step() {
        return step;
    }

    void show(Step next, String requestLabel, StepResult result) {
        loading = true;
        step = next;
        if (next == null) {
            setContent(placeholder);
            loading = false;
            return;
        }
        request.setText("Request: " + requestLabel);
        name.setText(next.name());
        delay.setText(next.delayMillis() == 0 ? "" : String.valueOf(next.delayMillis()));
        policy.setValue(next.onFailure() == null ? DEFAULT_POLICY
                : next.onFailure() == OnFailure.STOP ? "Stop" : "Continue");
        headerValues = next.headers();
        extractorValues = next.extractors();
        assertionValues = next.assertions();
        headers.set(next.headers());
        extractors.set(next.extractors());
        assertions.set(next.assertions());
        setContent(content);
        loading = false;
        showResult(result);
    }

    void showResult(StepResult result) {
        if (result == null) {
            status.setText("Not run yet");
            showBody("plaintext", "");
            return;
        }
        String text = result.state().name().toLowerCase();
        if (result.status() > 0) {
            text += "  " + result.status();
        }
        if (!result.message().isBlank()) {
            text += "  " + result.message();
        }
        status.setText(text);
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
        Step next = new Step(step.id(), step.requestId(), name.getText(), extractorValues, assertionValues,
                headerValues, delayValue, onFailure, step.x(), step.y());
        step = next;
        onChange.accept(next);
    }
}
