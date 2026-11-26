package io.github.itech_mm.apiro.ui.component;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.FxUse;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.FxScope;
import io.github.itech_framework.java_fx.annotations.components.Root;
import io.github.itech_framework.java_fx.loader.FxProvider;
import io.github.itech_mm.apiro.model.KeyValue;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;

@FxComponent("/views/components/key-value-table.fxml")
public class KeyValueTableView {

    @FXML
    @Root
    private VBox root;

    @FXML
    private VBox rowBox;

    @FxUse(scope = FxScope.PROTOTYPE)
    private FxProvider<KeyValueRowView> rowViews;

    private final ObservableList<KeyValueRow> rows = FXCollections.observableArrayList(
            row -> new Observable[]{row.keyProperty(), row.valueProperty(), row.enabledProperty()});
    private final Map<KeyValueRow, KeyValueRowView> views = new IdentityHashMap<>();

    @OnInit
    private void init() {
        rows.addListener((ListChangeListener<KeyValueRow>) this::onRowsChanged);
    }

    public VBox getRoot() {
        return root;
    }

    public ObservableList<KeyValueRow> getRows() {
        return rows;
    }

    public void setItems(List<KeyValue> items) {
        rows.setAll(items.stream().map(KeyValueRow::from).toList());
    }

    public List<KeyValue> getItems() {
        return rows.stream()
                .filter(row -> !row.getKey().isBlank() || !row.getValue().isBlank())
                .map(KeyValueRow::toKeyValue)
                .toList();
    }

    public void addRow() {
        KeyValueRow row = new KeyValueRow();
        rows.add(row);
        KeyValueRowView view = views.get(row);
        Platform.runLater(view::focusKey);
    }

    @FXML
    private void onAdd() {
        addRow();
    }

    private void onRowsChanged(ListChangeListener.Change<? extends KeyValueRow> change) {
        while (change.next()) {
            if (change.wasUpdated()) {
                continue;
            }
            for (KeyValueRow removed : change.getRemoved()) {
                KeyValueRowView view = views.remove(removed);
                if (view != null) {
                    rowBox.getChildren().remove(view.getRoot());
                    rowViews.release(view);
                }
            }
            int index = change.getFrom();
            for (KeyValueRow added : change.getAddedSubList()) {
                Runnable remove = () -> rows.remove(added);
                KeyValueRowView view = rowViews.create(added, remove);
                views.put(added, view);
                rowBox.getChildren().add(index++, view.getRoot());
            }
        }
    }
}