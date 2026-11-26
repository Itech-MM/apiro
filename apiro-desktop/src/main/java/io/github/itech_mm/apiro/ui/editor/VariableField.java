package io.github.itech_mm.apiro.ui.editor;

import io.github.itech_framework.code_editor.CodeEditor;
import io.github.itech_mm.apiro.ui.EditorStyle;
import io.github.itech_mm.apiro.ui.MonoFont;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;

public class VariableField extends CodeEditor {

    private static final VariableCompletionProvider COMPLETION = new VariableCompletionProvider();

    private static final VariableDiagnosticsProvider DIAGNOSTICS = new VariableDiagnosticsProvider();

    private final StringProperty value = new SimpleStringProperty("");

    private EventHandler<ActionEvent> onAction;

    private boolean syncing;

    public VariableField() {
        setSingleLine(true);
        setShowLineNumbers(false);
        EditorStyle.follow(this, "light-plus");
        setFontSize(MonoFont.EDITOR_SIZE);
        setCompletionProvider(COMPLETION);
        setCompletionAutoOpen(true);
        setCompletionMinChars(1);
        setDiagnosticsProvider(DIAGNOSTICS);
        getStyleClass().add("variable-field");
        setMinHeight(30);
        setPrefHeight(30);
        textProperty().addListener((obs, old, now) -> {
            if (!syncing) {
                syncing = true;
                value.set(now);
                syncing = false;
            }
        });
        value.addListener((obs, old, now) -> {
            String next = now == null ? "" : now;
            if (!syncing && !next.equals(getText())) {
                syncing = true;
                setText(next);
                syncing = false;
            }
        });
        addEventHandler(ActionEvent.ACTION, event -> {
            if (onAction != null) {
                onAction.handle(event);
            }
        });
        VariableStyler.install(this);
    }

    public StringProperty valueProperty() {
        return value;
    }

    public void setOnAction(EventHandler<ActionEvent> handler) {
        this.onAction = handler;
    }

    public void clear() {
        setText("");
    }
}