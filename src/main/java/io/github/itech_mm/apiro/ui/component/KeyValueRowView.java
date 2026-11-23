package io.github.itech_mm.apiro.ui.component;

import io.github.itech_framework.core.annotations.constructor.DefaultConstructor;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.core.annotations.methods.PreDestroy;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import io.github.itech_mm.apiro.ui.editor.VariableField;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.HBox;

@FxComponent("/views/components/key-value-row.fxml")
public class KeyValueRowView {

    private static final String DISABLED_STYLE = "kv-disabled";

    @FXML
    @Root
    private HBox root;

    @FXML
    private CheckBox enabledCheck;

    @FXML
    private VariableField keyField;

    @FXML
    private VariableField valueField;

    private KeyValueRow row;
    private Runnable onRemove;
    private final ChangeListener<Boolean> enabledListener = (observable, oldValue, newValue) -> syncStyle();

    @DefaultConstructor
    public KeyValueRowView() {
    }

    public KeyValueRowView(KeyValueRow row, Runnable onRemove) {
        this.row = row;
        this.onRemove = onRemove;
    }

    @OnInit
    private void init() {
        enabledCheck.selectedProperty().bindBidirectional(row.enabledProperty());
        keyField.valueProperty().bindBidirectional(row.keyProperty());
        valueField.valueProperty().bindBidirectional(row.valueProperty());
        row.enabledProperty().addListener(enabledListener);
        syncStyle();
    }

    @PreDestroy
    private void destroy() {
        enabledCheck.selectedProperty().unbindBidirectional(row.enabledProperty());
        keyField.valueProperty().unbindBidirectional(row.keyProperty());
        valueField.valueProperty().unbindBidirectional(row.valueProperty());
        row.enabledProperty().removeListener(enabledListener);
    }

    public HBox getRoot() {
        return root;
    }

    public void focusKey() {
        keyField.requestFocus();
    }

    @FXML
    private void onRemoveClicked() {
        onRemove.run();
    }

    private void syncStyle() {
        root.getStyleClass().remove(DISABLED_STYLE);
        if (!row.isEnabled()) {
            root.getStyleClass().add(DISABLED_STYLE);
        }
    }
}