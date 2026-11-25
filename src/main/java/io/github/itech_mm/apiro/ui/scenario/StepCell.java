package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.service.scenario.StepResult;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

final class StepCell extends ListCell<Step> {

    static final DataFormat STEP = new DataFormat("application/x-apiro-step");

    interface Support {

        Optional<RequestItem> request(String requestId);

        StepResult.State state(String stepId);

        long millis(String stepId);

        int status(String stepId);

        String detail(String stepId);

        void move(String stepId, int index);

        void remove(String stepId);
    }

    private final Support support;
    private final Label dot = new Label();
    private final Label number = new Label();
    private final Label badge = new Label();
    private final Label name = new Label();
    private final Label time = new Label();
    private final HBox row = new HBox(8);
    private final ContextMenu menu = new ContextMenu();

    StepCell(Support support) {
        this.support = support;
        dot.getStyleClass().add("step-dot");
        number.getStyleClass().add("muted");
        number.setMinWidth(22);
        time.getStyleClass().add("muted");
        name.setMaxWidth(Double.MAX_VALUE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 6, 4, 6));
        row.getChildren().addAll(dot, number, badge, name, spacer, time);

        MenuItem up = new MenuItem("Move up");
        up.setOnAction(event -> {
            if (getItem() != null) {
                support.move(getItem().id(), getIndex() - 1);
            }
        });
        MenuItem down = new MenuItem("Move down");
        down.setOnAction(event -> {
            if (getItem() != null) {
                support.move(getItem().id(), getIndex() + 1);
            }
        });
        MenuItem remove = new MenuItem("Remove step");
        remove.setOnAction(event -> {
            if (getItem() != null) {
                support.remove(getItem().id());
            }
        });
        menu.getItems().addAll(up, down, remove);

        setOnDragDetected(event -> {
            if (getItem() == null) {
                return;
            }
            Dragboard board = startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(STEP, getItem().id());
            board.setContent(content);
            event.consume();
        });
        setOnDragOver(event -> {
            if (getItem() != null && event.getDragboard().hasContent(STEP)) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        setOnDragDropped(event -> {
            Dragboard board = event.getDragboard();
            if (getItem() != null && board.hasContent(STEP)) {
                support.move((String) board.getContent(STEP), getIndex());
                event.setDropCompleted(true);
                event.consume();
            }
        });
    }

    @Override
    protected void updateItem(Step step, boolean empty) {
        super.updateItem(step, empty);
        if (empty || step == null) {
            setGraphic(null);
            setContextMenu(null);
            return;
        }
        Optional<RequestItem> request = support.request(step.requestId());
        number.setText((getIndex() + 1) + ".");
        badge.getStyleClass().removeIf(style -> style.startsWith("method-"));
        badge.getStyleClass().add("method-badge");
        name.getStyleClass().remove("step-missing");
        if (request.isPresent()) {
            badge.setText(request.get().method().name());
            badge.getStyleClass().add("method-" + request.get().method().name().toLowerCase());
            name.setText(step.name().isBlank() ? request.get().name() : step.name());
        } else {
            badge.setText("?");
            name.setText("Missing request");
            name.getStyleClass().add("step-missing");
        }
        dot.getStyleClass().removeIf(style -> style.startsWith("state-"));
        StepResult.State state = support.state(step.id());
        if (state != null) {
            dot.getStyleClass().add("state-" + state.name().toLowerCase());
        }
        long millis = support.millis(step.id());
        time.setText(millis <= 0 ? "" : millis < 1000 ? millis + " ms" : String.format("%.1f s", millis / 1000.0));
        setContextMenu(menu);
        setGraphic(row);
    }
}
