package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.service.scenario.StepResult;
import io.github.itech_mm.apiro.service.scenario.StepSummary;
import io.github.itech_mm.apiro.service.scenario.VariableUsage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;
import javafx.util.Duration;

final class DiagramView extends StackPane {

    interface Actions {

        Optional<RequestItem> dropped(Dragboard board);

        void add(String requestId, int index);

        void undo();

        void redo();

        void run();

        void stop();

        void log(boolean shown);

        void focusInspector();
    }

    private static final double NODE_WIDTH = 230;
    private static final double NODE_HEIGHT = 100;
    private static final double GAP = 80;
    private static final double ROW_GAP = 70;
    private static final double MARGIN = 40;
    private static final double TARGET_ZOOM = 0.8;
    private static final int MIN_COLUMNS = 2;
    private static final int MAX_COLUMNS = 8;
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 2.5;
    private static final double INITIAL_FLOOR = 0.6;

    private final StepCell.Support support;
    private final Consumer<String> onSelect;
    private final Actions actions;
    private final Rectangle marker = new Rectangle(4, NODE_HEIGHT + 24);
    private final Pane viewport = new Pane();
    private final Group world = new Group();
    private final Group edgeLayer = new Group();
    private final Group nodeLayer = new Group();
    private final Group dataLayer = new Group();
    private final Affine view = new Affine();
    private final Map<String, StepNode> nodes = new LinkedHashMap<>();
    private final List<Link> links = new ArrayList<>();
    private final Label empty = new Label("Add steps to see them here");
    private final Label zoomLabel = new Label("100%");
    private final HBox zoomTools;
    private final Button runButton = tool("▶ Run");
    private final Button stopButton = tool("■ Stop");
    private final ToggleButton logButton = new ToggleButton("Logs");
    private List<Step> steps = List.of();
    private VariableUsage.Result usage = VariableUsage.Result.EMPTY;
    private String selectedId;
    private double zoom = 1;
    private double panX = MARGIN;
    private double panY = MARGIN;
    private double dragX;
    private double dragY;
    private boolean fitPending = true;
    private int cols = 4;

    DiagramView(StepCell.Support support, Consumer<String> onSelect, Actions actions) {
        this.support = support;
        this.onSelect = onSelect;
        this.actions = actions;
        setFocusTraversable(true);
        marker.getStyleClass().add("diagram-drop-marker");
        marker.setArcWidth(4);
        marker.setArcHeight(4);
        marker.setVisible(false);
        marker.setMouseTransparent(true);
        getStyleClass().add("diagram-view");
        viewport.getStyleClass().add("diagram-viewport");
        viewport.setMinSize(0, 0);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(viewport.widthProperty());
        clip.heightProperty().bind(viewport.heightProperty());
        viewport.setClip(clip);
        dataLayer.setMouseTransparent(true);
        world.getChildren().addAll(edgeLayer, nodeLayer, dataLayer, marker);
        world.getTransforms().add(view);
        viewport.getChildren().add(world);

        empty.getStyleClass().add("muted");
        empty.setMouseTransparent(true);

        Button zoomOut = tool("−");
        Button zoomIn = tool("+");
        Button fit = tool("Fit");
        zoomLabel.getStyleClass().add("muted");
        zoomLabel.setMinWidth(40);
        zoomLabel.setAlignment(Pos.CENTER);
        zoomOut.setOnAction(event -> zoomAt(viewport.getWidth() / 2, viewport.getHeight() / 2, 1 / 1.2));
        zoomIn.setOnAction(event -> zoomAt(viewport.getWidth() / 2, viewport.getHeight() / 2, 1.2));
        fit.setOnAction(event -> fit(MIN_ZOOM));
        zoomTools = new HBox(4, zoomOut, zoomLabel, zoomIn, fit);
        HBox tools = zoomTools;
        tools.setAlignment(Pos.CENTER);
        tools.getStyleClass().add("diagram-tools");
        tools.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(tools, Pos.TOP_RIGHT);
        StackPane.setMargin(tools, new Insets(10));

        runButton.getStyleClass().add("run");
        stopButton.getStyleClass().add("stop");
        logButton.getStyleClass().add("diagram-tool");
        runButton.setTooltip(new Tooltip("Run the scenario"));
        stopButton.setTooltip(new Tooltip("Stop the run"));
        logButton.setTooltip(new Tooltip("Show or hide the run log"));
        runButton.setOnAction(event -> actions.run());
        stopButton.setOnAction(event -> actions.stop());
        logButton.setOnAction(event -> actions.log(logButton.isSelected()));
        HBox runTools = new HBox(4, runButton, stopButton, logButton);
        runTools.setAlignment(Pos.CENTER);
        runTools.getStyleClass().add("diagram-tools");
        runTools.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(runTools, Pos.TOP_LEFT);
        StackPane.setMargin(runTools, new Insets(10));

        getChildren().addAll(viewport, empty, tools, runTools);

        setOnKeyPressed(event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.Z && !event.isShiftDown()) {
                actions.undo();
                event.consume();
            } else if (event.isShortcutDown() && (event.getCode() == KeyCode.Y
                    || event.getCode() == KeyCode.Z && event.isShiftDown())) {
                actions.redo();
                event.consume();
            } else if ((event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) && selectedId != null) {
                support.remove(selectedId);
                event.consume();
            }
        });
        viewport.setOnDragOver(event -> {
            Dragboard board = event.getDragboard();
            if (board.hasContent(StepCell.STEP) || actions.dropped(board).isPresent()) {
                event.acceptTransferModes(TransferMode.MOVE);
                showMarker(gapAt(event.getSceneX(), event.getSceneY()));
                event.consume();
            }
        });
        viewport.setOnDragExited(event -> marker.setVisible(false));
        viewport.setOnDragDropped(event -> {
            Dragboard board = event.getDragboard();
            int gap = gapAt(event.getSceneX(), event.getSceneY());
            marker.setVisible(false);
            if (board.hasContent(StepCell.STEP)) {
                String id = (String) board.getContent(StepCell.STEP);
                int from = indexOf(id);
                if (from >= 0) {
                    support.move(id, DiagramLayout.targetAfterRemoval(from, gap));
                    event.setDropCompleted(true);
                }
            } else {
                Optional<RequestItem> request = actions.dropped(board);
                if (request.isPresent()) {
                    actions.add(request.get().id(), gap);
                    event.setDropCompleted(true);
                }
            }
            event.consume();
        });
        viewport.setOnMousePressed(event -> {
            requestFocus();
            dragX = event.getX();
            dragY = event.getY();
            viewport.setCursor(Cursor.CLOSED_HAND);
        });
        viewport.setOnMouseDragged(event -> {
            panX += event.getX() - dragX;
            panY += event.getY() - dragY;
            dragX = event.getX();
            dragY = event.getY();
            apply();
        });
        viewport.setOnMouseReleased(event -> viewport.setCursor(Cursor.DEFAULT));
        viewport.setOnScroll(event -> {
            if (event.getDeltaY() != 0) {
                zoomAt(event.getX(), event.getY(), event.getDeltaY() > 0 ? 1.1 : 1 / 1.1);
            }
            event.consume();
        });
        viewport.widthProperty().addListener((obs, oldValue, newValue) -> tryFit());
        viewport.heightProperty().addListener((obs, oldValue, newValue) -> tryFit());
        apply();
    }

    private static Button tool(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("diagram-tool");
        return button;
    }

    void show(List<Step> next, String selected, boolean refit, VariableUsage.Result analysis) {
        steps = List.copyOf(next);
        usage = analysis;
        selectedId = selected;
        nodes.values().forEach(StepNode::dispose);
        links.forEach(Link::dispose);
        nodeLayer.getChildren().clear();
        edgeLayer.getChildren().clear();
        nodes.clear();
        links.clear();
        for (int i = 0; i < steps.size(); i++) {
            StepNode node = new StepNode(steps.get(i), i);
            nodes.put(steps.get(i).id(), node);
            nodeLayer.getChildren().add(node);
            if (i > 0) {
                Link link = new Link(steps.get(i - 1).id(), steps.get(i).id(), i - 1);
                links.add(link);
                edgeLayer.getChildren().add(link.group);
            }
        }
        empty.setVisible(steps.isEmpty());
        relayout();
        refresh();
        if (refit) {
            fitPending = true;
            tryFit();
        }
    }

    private int indexOf(String stepId) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).id().equals(stepId)) {
                return i;
            }
        }
        return -1;
    }

    private void relayout() {
        int index = 0;
        for (StepNode node : nodes.values()) {
            node.setLayoutX(DiagramLayout.nodeX(index, cols, NODE_WIDTH, GAP));
            node.setLayoutY(DiagramLayout.nodeY(index, cols, NODE_HEIGHT, ROW_GAP));
            node.placePorts(index, steps.size());
            index++;
        }
        links.forEach(Link::place);
        refreshData();
    }

    private int gapAt(double sceneX, double sceneY) {
        Point2D local = viewport.sceneToLocal(sceneX, sceneY);
        double worldX = (local.getX() - panX) / zoom;
        double worldY = (local.getY() - panY) / zoom;
        return DiagramLayout.nearestGap(worldX, worldY, steps.size(), cols, NODE_WIDTH, NODE_HEIGHT, GAP, ROW_GAP);
    }

    private void showMarker(int gap) {
        DiagramLayout.Point anchor = DiagramLayout.gapAnchor(gap, steps.size(), cols, NODE_WIDTH, NODE_HEIGHT, GAP,
                ROW_GAP);
        marker.setX(anchor.x() - 2);
        marker.setY(anchor.y() - marker.getHeight() / 2);
        marker.setVisible(true);
    }

    void setRightInset(double inset) {
        StackPane.setMargin(zoomTools, new Insets(10, 10 + inset, 10, 10));
    }

    void syncRun(boolean runEnabled, boolean stopEnabled) {
        runButton.setDisable(!runEnabled);
        stopButton.setDisable(!stopEnabled);
    }

    void syncLog(boolean shown) {
        logButton.setSelected(shown);
    }

    void select(String stepId) {
        selectedId = stepId;
        nodes.values().forEach(StepNode::syncSelected);
        refreshData();
    }

    void setUsage(VariableUsage.Result analysis) {
        usage = analysis;
        refresh();
    }

    void refresh() {
        nodes.values().forEach(StepNode::refresh);
        links.forEach(Link::refresh);
        refreshData();
    }

    private void refreshData() {
        dataLayer.getChildren().clear();
        boolean running = false;
        for (Step step : steps) {
            if (support.state(step.id()) == StepResult.State.RUNNING) {
                running = true;
                break;
            }
        }
        for (VariableUsage.Link link : usage.links()) {
            boolean inbound;
            boolean visible;
            if (running) {
                inbound = true;
                visible = support.state(link.toStepId()) == StepResult.State.RUNNING;
            } else if (selectedId == null) {
                inbound = false;
                visible = false;
            } else {
                inbound = link.toStepId().equals(selectedId);
                visible = inbound || link.fromStepId().equals(selectedId);
            }
            if (visible) {
                addDataLink(link, inbound);
            }
        }
    }

    private void addDataLink(VariableUsage.Link link, boolean inbound) {
        int from = indexOf(link.fromStepId());
        int to = indexOf(link.toStepId());
        if (from < 0 || to < 0) {
            return;
        }
        double fx = DiagramLayout.nodeX(from, cols, NODE_WIDTH, GAP);
        double fy = DiagramLayout.nodeY(from, cols, NODE_HEIGHT, ROW_GAP);
        double tx = DiagramLayout.nodeX(to, cols, NODE_WIDTH, GAP);
        double ty = DiagramLayout.nodeY(to, cols, NODE_HEIGHT, ROW_GAP);
        boolean sameRow = DiagramLayout.row(from, cols) == DiagramLayout.row(to, cols);
        double shift = sameRow ? 0 : 36;
        double x1 = fx + NODE_WIDTH / 2 + shift;
        double y1 = fy + NODE_HEIGHT;
        double x2 = tx + NODE_WIDTH / 2 + shift;
        double y2 = sameRow ? ty + NODE_HEIGHT : ty;
        double c1y;
        double c2y;
        double uy;
        if (sameRow) {
            double drop = 46 + Math.min(60, Math.abs(x2 - x1) * 0.12);
            c1y = y1 + drop;
            c2y = y2 + drop;
            uy = -1;
        } else {
            double handle = Math.max(30, (y2 - y1) / 2);
            c1y = y1 + handle;
            c2y = y2 - handle;
            uy = 1;
        }
        CubicCurve curve = new CubicCurve(x1, y1, x1, c1y, x2, c2y, x2, y2);
        curve.setFill(null);
        curve.getStrokeDashArray().setAll(2.0, 5.0);
        curve.getStyleClass().addAll("data-edge", inbound ? "inbound" : "outbound");
        Polygon arrow = new Polygon(x2, y2, x2 - 5, y2 - uy * 10, x2 + 5, y2 - uy * 10);
        arrow.getStyleClass().addAll("data-arrow", inbound ? "inbound" : "outbound");
        String text = String.join(", ", link.names());
        if (text.length() > 32) {
            text = text.substring(0, 31) + "…";
        }
        Label label = new Label(text);
        label.getStyleClass().addAll("data-label", inbound ? "inbound" : "outbound");
        double width = text.length() * 6.4 + 14;
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.setMaxWidth(width);
        label.setAlignment(Pos.CENTER);
        double midX = (x1 + 3 * x1 + 3 * x2 + x2) / 8;
        double midY = (y1 + 3 * c1y + 3 * c2y + y2) / 8;
        label.setLayoutX(midX - width / 2);
        label.setLayoutY(midY - 9);
        dataLayer.getChildren().addAll(curve, arrow, label);
    }

    void refit() {
        fitPending = true;
        tryFit();
    }

    private void tryFit() {
        if (fitPending && viewport.getWidth() > 50 && viewport.getHeight() > 50) {
            fitPending = false;
            fit(INITIAL_FLOOR);
        }
    }

    private void fit(double floor) {
        double width = viewport.getWidth();
        double height = viewport.getHeight();
        if (steps.isEmpty() || width <= 0 || height <= 0) {
            zoom = 1;
            panX = MARGIN;
            panY = MARGIN;
            apply();
            return;
        }
        int columns = DiagramLayout.columns(width, TARGET_ZOOM, MARGIN, NODE_WIDTH, GAP, MIN_COLUMNS, MAX_COLUMNS);
        if (columns != cols) {
            cols = columns;
            relayout();
        }
        double contentWidth = DiagramLayout.contentWidth(steps.size(), cols, NODE_WIDTH, GAP);
        double contentHeight = DiagramLayout.contentHeight(steps.size(), cols, NODE_HEIGHT, ROW_GAP);
        double fitted = Math.min((width - 2 * MARGIN) / contentWidth, (height - 2 * MARGIN) / contentHeight);
        zoom = clamp(Math.max(Math.min(fitted, 1), floor));
        double scaledWidth = contentWidth * zoom;
        double scaledHeight = contentHeight * zoom;
        panX = scaledWidth + 2 * MARGIN <= width ? (width - scaledWidth) / 2 : MARGIN;
        panY = scaledHeight + 2 * MARGIN <= height ? (height - scaledHeight) / 2 : MARGIN;
        apply();
    }

    private void zoomAt(double px, double py, double factor) {
        double next = clamp(zoom * factor);
        double ratio = next / zoom;
        panX = px - (px - panX) * ratio;
        panY = py - (py - panY) * ratio;
        zoom = next;
        apply();
    }

    private void apply() {
        view.setToTransform(zoom, 0, panX, 0, zoom, panY);
        zoomLabel.setText(Math.round(zoom * 100) + "%");
    }

    private static double clamp(double value) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
    }

    private final class Link {

        final String fromId;
        final String toId;
        final CubicCurve curve;
        final Polygon arrow;
        final Group group;
        private final Timeline flow;

        private final int from;

        Link(String fromId, String toId, int from) {
            this.fromId = fromId;
            this.toId = toId;
            this.from = from;
            curve = new CubicCurve();
            curve.setFill(null);
            curve.getStyleClass().add("diagram-edge");
            arrow = new Polygon();
            arrow.getStyleClass().add("diagram-arrow");
            group = new Group(curve, arrow);
            group.setMouseTransparent(true);
            flow = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(curve.strokeDashOffsetProperty(), 0)),
                    new KeyFrame(Duration.millis(450),
                            new KeyValue(curve.strokeDashOffsetProperty(), -14, Interpolator.LINEAR)));
            flow.setCycleCount(Animation.INDEFINITE);
            place();
            refresh();
        }

        void place() {
            int to = from + 1;
            double fx = DiagramLayout.nodeX(from, cols, NODE_WIDTH, GAP);
            double fy = DiagramLayout.nodeY(from, cols, NODE_HEIGHT, ROW_GAP);
            double tx = DiagramLayout.nodeX(to, cols, NODE_WIDTH, GAP);
            double ty = DiagramLayout.nodeY(to, cols, NODE_HEIGHT, ROW_GAP);
            double x1;
            double y1;
            double x2;
            double y2;
            double ux;
            double uy;
            if (DiagramLayout.turns(from, cols)) {
                x1 = fx + NODE_WIDTH / 2;
                y1 = fy + NODE_HEIGHT;
                x2 = tx + NODE_WIDTH / 2;
                y2 = ty;
                double handle = (y2 - y1) / 2;
                curve.setControlX1(x1);
                curve.setControlY1(y1 + handle);
                curve.setControlX2(x2);
                curve.setControlY2(y2 - handle);
                ux = 0;
                uy = 1;
            } else {
                int direction = DiagramLayout.direction(from, cols);
                x1 = direction > 0 ? fx + NODE_WIDTH : fx;
                x2 = direction > 0 ? tx : tx + NODE_WIDTH;
                y1 = fy + NODE_HEIGHT / 2;
                y2 = y1;
                double handle = (x2 - x1) / 2;
                curve.setControlX1(x1 + handle);
                curve.setControlY1(y1);
                curve.setControlX2(x2 - handle);
                curve.setControlY2(y2);
                ux = direction;
                uy = 0;
            }
            curve.setStartX(x1);
            curve.setStartY(y1);
            curve.setEndX(x2);
            curve.setEndY(y2);
            double baseX = x2 - ux * 10;
            double baseY = y2 - uy * 10;
            double px = -uy * 5.5;
            double py = ux * 5.5;
            arrow.getPoints().setAll(x2, y2, baseX + px, baseY + py, baseX - px, baseY - py);
        }

        void refresh() {
            StepResult.State source = support.state(fromId);
            StepResult.State target = support.state(toId);
            String kind = "idle";
            if (target == StepResult.State.RUNNING) {
                kind = "active";
            } else if (source == StepResult.State.FAILED) {
                kind = "failed";
            } else if (source == StepResult.State.PASSED
                    && (target == StepResult.State.PASSED || target == StepResult.State.FAILED)) {
                kind = "done";
            } else if (source == StepResult.State.CANCELLED || source == StepResult.State.SKIPPED
                    || target == StepResult.State.SKIPPED || target == StepResult.State.CANCELLED) {
                kind = "dim";
            }
            curve.getStyleClass().removeIf(style -> style.startsWith("edge-"));
            arrow.getStyleClass().removeIf(style -> style.startsWith("edge-"));
            curve.getStyleClass().add("edge-" + kind);
            arrow.getStyleClass().add("edge-" + kind);
            if (kind.equals("active")) {
                curve.getStrokeDashArray().setAll(8.0, 6.0);
                flow.play();
            } else {
                flow.stop();
                curve.setStrokeDashOffset(0);
                if (kind.equals("dim")) {
                    curve.getStrokeDashArray().setAll(3.0, 6.0);
                } else {
                    curve.getStrokeDashArray().clear();
                }
            }
        }

        void dispose() {
            flow.stop();
        }
    }

    private final class StepNode extends StackPane {

        private final Step step;
        private final Label number = new Label();
        private final Label name = new Label();
        private final Label time = new Label();
        private final Label badge = new Label();
        private final Label warn = new Label("!");
        private final Tooltip warnTip = new Tooltip();
        private final Label url = new Label();
        private final Label pill = new Label();
        private final Circle portIn = new Circle(5);
        private final Circle portOut = new Circle(5);
        private final FadeTransition pulse = new FadeTransition(Duration.millis(700), pill);
        private final Tooltip tip = new Tooltip();
        private boolean tipInstalled;

        StepNode(Step step, int index) {
            this.step = step;
            getStyleClass().add("diagram-node");
            setMinSize(NODE_WIDTH, NODE_HEIGHT);
            setPrefSize(NODE_WIDTH, NODE_HEIGHT);
            setMaxSize(NODE_WIDTH, NODE_HEIGHT);
            number.setText((index + 1) + "");
            number.getStyleClass().add("diagram-node-number");
            name.getStyleClass().add("diagram-node-title");
            name.setMaxWidth(Double.MAX_VALUE);
            time.getStyleClass().add("diagram-node-time");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            warn.getStyleClass().add("diagram-warn");
            warn.setTooltip(warnTip);
            warn.setVisible(false);
            warn.setManaged(false);
            HBox head = new HBox(8, number, name, spacer, warn, time);
            head.setAlignment(Pos.CENTER_LEFT);
            head.getStyleClass().add("diagram-node-head");
            HBox.setHgrow(name, Priority.SOMETIMES);

            url.getStyleClass().add("diagram-node-url");
            url.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(url, Priority.ALWAYS);
            HBox line = new HBox(6, badge, url);
            line.setAlignment(Pos.CENTER_LEFT);
            pill.getStyleClass().add("diagram-pill");
            VBox body = new VBox(8, line, pill);
            body.getStyleClass().add("diagram-node-body");
            VBox card = new VBox(head, body);
            card.getStyleClass().add("diagram-card");
            card.setMinSize(NODE_WIDTH, NODE_HEIGHT);
            card.setMaxSize(NODE_WIDTH, NODE_HEIGHT);

            portIn.getStyleClass().add("diagram-port");
            portOut.getStyleClass().add("diagram-port");
            portIn.setMouseTransparent(true);
            portOut.setMouseTransparent(true);
            getChildren().addAll(card, portIn, portOut);

            pulse.setFromValue(1);
            pulse.setToValue(0.35);
            pulse.setAutoReverse(true);
            pulse.setCycleCount(Animation.INDEFINITE);

            MenuItem left = new MenuItem("Move left");
            left.setOnAction(event -> support.move(step.id(), indexOf(step.id()) - 1));
            MenuItem right = new MenuItem("Move right");
            right.setOnAction(event -> support.move(step.id(), indexOf(step.id()) + 1));
            MenuItem remove = new MenuItem("Remove step");
            remove.setOnAction(event -> support.remove(step.id()));
            ContextMenu menu = new ContextMenu(left, right, remove);
            setOnContextMenuRequested(event -> {
                selectedId = step.id();
                nodes.values().forEach(StepNode::syncSelected);
                onSelect.accept(step.id());
                menu.show(this, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            setOnDragDetected(event -> {
                Dragboard board = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.put(StepCell.STEP, step.id());
                board.setContent(content);
                event.consume();
            });
            setOnDragDone(event -> marker.setVisible(false));
            setOnMousePressed(event -> {
                DiagramView.this.requestFocus();
                if (event.getButton() == MouseButton.PRIMARY) {
                    selectedId = step.id();
                    nodes.values().forEach(StepNode::syncSelected);
                    onSelect.accept(step.id());
                }
                event.consume();
            });
            setOnMouseDragged(event -> event.consume());
            setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    actions.focusInspector();
                }
                event.consume();
            });
            tip.setShowDelay(Duration.millis(400));
            tip.setShowDuration(Duration.INDEFINITE);
            tip.setWrapText(true);
            tip.setMaxWidth(420);
        }

        private void updateTip(String text) {
            tip.setText(text);
            if (text.isEmpty() && tipInstalled) {
                Tooltip.uninstall(this, tip);
                tipInstalled = false;
            } else if (!text.isEmpty() && !tipInstalled) {
                Tooltip.install(this, tip);
                tipInstalled = true;
            }
        }

        void placePorts(int index, int count) {
            int direction = DiagramLayout.direction(index, cols);
            Pos in = index > 0 && index % cols == 0 ? Pos.TOP_CENTER
                    : direction > 0 ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT;
            Pos out = index < count - 1 && DiagramLayout.turns(index, cols) ? Pos.BOTTOM_CENTER
                    : direction > 0 ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT;
            place(portIn, in);
            place(portOut, out);
        }

        private void place(Circle port, Pos side) {
            StackPane.setAlignment(port, side);
            port.setTranslateX(side == Pos.CENTER_LEFT ? -5 : side == Pos.CENTER_RIGHT ? 5 : 0);
            port.setTranslateY(side == Pos.TOP_CENTER ? -5 : side == Pos.BOTTOM_CENTER ? 5 : 0);
        }

        void dispose() {
            pulse.stop();
        }

        void syncSelected() {
            getStyleClass().remove("selected");
            if (step.id().equals(selectedId)) {
                getStyleClass().add("selected");
            }
        }

        void refresh() {
            Optional<RequestItem> request = support.request(step.requestId());
            getStyleClass().removeIf(style -> style.startsWith("method-"));
            badge.getStyleClass().removeIf(style -> style.startsWith("method-"));
            badge.getStyleClass().add("method-badge");
            name.getStyleClass().remove("step-missing");
            if (request.isPresent()) {
                String method = request.get().method().name().toLowerCase();
                badge.setText(request.get().method().name());
                badge.getStyleClass().add("method-" + method);
                getStyleClass().add("method-" + method);
                name.setText(step.name().isBlank() ? request.get().name() : step.name());
                url.setText(request.get().url() == null ? "" : request.get().url());
            } else {
                badge.setText("?");
                name.setText("Missing request");
                name.getStyleClass().add("step-missing");
                url.setText("");
            }
            getStyleClass().removeIf(style -> style.startsWith("state-"));
            pill.getStyleClass().removeIf(style -> style.startsWith("pill-"));
            portOut.getStyleClass().removeIf(style -> style.startsWith("state-"));
            portIn.getStyleClass().removeIf(style -> style.startsWith("state-"));
            StepResult.State state = support.state(step.id());
            int status = support.status(step.id());
            String code = status > 0 ? "  " + status : "";
            String text = "Not run";
            if (state != null) {
                String key = state.name().toLowerCase();
                getStyleClass().add("state-" + key);
                portOut.getStyleClass().add("state-" + key);
                portIn.getStyleClass().add("state-" + key);
                pill.getStyleClass().add("pill-" + key);
                text = switch (state) {
                    case PENDING -> "Waiting";
                    case RUNNING -> "Running…";
                    case PASSED -> "✓ Passed" + code;
                    case FAILED -> "✕ Failed" + code;
                    case SKIPPED -> "– Skipped";
                    case CANCELLED -> "■ Cancelled";
                };
            }
            pill.setText(text);
            List<String> missing = usage.unresolvedFor(step.id());
            warn.setVisible(!missing.isEmpty());
            warn.setManaged(!missing.isEmpty());
            warnTip.setText(missing.isEmpty() ? "" : "Not defined before this step: " + String.join(", ", missing));
            if (state == StepResult.State.RUNNING) {
                pulse.play();
            } else {
                pulse.stop();
                pill.setOpacity(1);
            }
            long millis = support.millis(step.id());
            time.setText(millis <= 0 ? "" : StepSummary.duration(millis));
            String address = request.isPresent() ? request.get().method().name() + " " + url.getText() : "";
            String detail = support.detail(step.id());
            updateTip(detail.isEmpty() ? address.trim() : (address + "\n\n" + detail).trim());
            syncSelected();
        }
    }
}
