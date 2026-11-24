package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.service.scenario.StepResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;

final class DiagramView extends StackPane {

    private static final double NODE_WIDTH = 210;
    private static final double NODE_HEIGHT = 64;
    private static final double GAP = 80;
    private static final double MARGIN = 40;
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 2.5;
    private static final double INITIAL_FLOOR = 0.6;

    private final StepCell.Support support;
    private final Consumer<String> onSelect;
    private final Pane viewport = new Pane();
    private final Group world = new Group();
    private final Group edgeLayer = new Group();
    private final Group nodeLayer = new Group();
    private final Affine view = new Affine();
    private final Map<String, StepNode> nodes = new LinkedHashMap<>();
    private final Label empty = new Label("Add steps to see them here");
    private final Label zoomLabel = new Label("100%");
    private List<Step> steps = List.of();
    private String selectedId;
    private double zoom = 1;
    private double panX = MARGIN;
    private double panY = MARGIN;
    private double dragX;
    private double dragY;
    private boolean fitPending = true;

    DiagramView(StepCell.Support support, Consumer<String> onSelect) {
        this.support = support;
        this.onSelect = onSelect;
        getStyleClass().add("diagram-view");
        viewport.getStyleClass().add("diagram-viewport");
        viewport.setMinSize(0, 0);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(viewport.widthProperty());
        clip.heightProperty().bind(viewport.heightProperty());
        viewport.setClip(clip);
        world.getChildren().addAll(edgeLayer, nodeLayer);
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
        HBox tools = new HBox(4, zoomOut, zoomLabel, zoomIn, fit);
        tools.setAlignment(Pos.CENTER);
        tools.getStyleClass().add("diagram-tools");
        tools.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(tools, Pos.TOP_RIGHT);
        StackPane.setMargin(tools, new Insets(10));

        getChildren().addAll(viewport, empty, tools);

        viewport.setOnMousePressed(event -> {
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

    void show(List<Step> next, String selected, boolean refit) {
        steps = List.copyOf(next);
        selectedId = selected;
        nodeLayer.getChildren().clear();
        edgeLayer.getChildren().clear();
        nodes.clear();
        for (int i = 0; i < steps.size(); i++) {
            StepNode node = new StepNode(steps.get(i), i);
            node.setLayoutX(i * (NODE_WIDTH + GAP));
            node.setLayoutY(0);
            nodes.put(steps.get(i).id(), node);
            nodeLayer.getChildren().add(node);
            if (i > 0) {
                edgeLayer.getChildren().add(edge(i - 1, i));
            }
        }
        empty.setVisible(steps.isEmpty());
        refresh();
        if (refit) {
            fitPending = true;
            tryFit();
        }
    }

    void select(String stepId) {
        selectedId = stepId;
        nodes.values().forEach(StepNode::syncSelected);
    }

    void refresh() {
        nodes.values().forEach(StepNode::refresh);
    }

    private Group edge(int from, int to) {
        double x1 = from * (NODE_WIDTH + GAP) + NODE_WIDTH;
        double x2 = to * (NODE_WIDTH + GAP);
        double y = NODE_HEIGHT / 2;
        double handle = (x2 - x1) / 2;
        CubicCurve curve = new CubicCurve(x1, y, x1 + handle, y, x2 - handle, y, x2, y);
        curve.setFill(null);
        curve.getStyleClass().add("diagram-edge");
        Polygon arrow = new Polygon(x2, y, x2 - 9, y - 5, x2 - 9, y + 5);
        arrow.getStyleClass().add("diagram-arrow");
        Group group = new Group(curve, arrow);
        group.setMouseTransparent(true);
        return group;
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
        double contentWidth = steps.size() * NODE_WIDTH + (steps.size() - 1) * GAP;
        double fitted = Math.min((width - 2 * MARGIN) / contentWidth, (height - 2 * MARGIN) / NODE_HEIGHT);
        zoom = clamp(Math.max(Math.min(fitted, 1), floor));
        double scaledWidth = contentWidth * zoom;
        panX = scaledWidth + 2 * MARGIN <= width ? (width - scaledWidth) / 2 : MARGIN;
        panY = (height - NODE_HEIGHT * zoom) / 2;
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

    private final class StepNode extends VBox {

        private final Step step;
        private final Label dot = new Label();
        private final Label number = new Label();
        private final Label badge = new Label();
        private final Label name = new Label();
        private final Label time = new Label();

        StepNode(Step step, int index) {
            super(6);
            this.step = step;
            getStyleClass().add("diagram-node");
            setMinSize(NODE_WIDTH, NODE_HEIGHT);
            setPrefSize(NODE_WIDTH, NODE_HEIGHT);
            setMaxSize(NODE_WIDTH, NODE_HEIGHT);
            dot.getStyleClass().add("step-dot");
            number.getStyleClass().add("muted");
            number.setText((index + 1) + ".");
            time.getStyleClass().add("muted");
            name.setMaxWidth(Double.MAX_VALUE);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox top = new HBox(8, dot, number, badge, spacer, time);
            top.setAlignment(Pos.CENTER_LEFT);
            getChildren().addAll(top, name);
            setOnMousePressed(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    selectedId = step.id();
                    nodes.values().forEach(StepNode::syncSelected);
                    onSelect.accept(step.id());
                }
                event.consume();
            });
            setOnMouseDragged(event -> event.consume());
        }

        void syncSelected() {
            getStyleClass().remove("selected");
            if (step.id().equals(selectedId)) {
                getStyleClass().add("selected");
            }
        }

        void refresh() {
            Optional<RequestItem> request = support.request(step.requestId());
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
            getStyleClass().removeIf(style -> style.startsWith("state-"));
            dot.getStyleClass().removeIf(style -> style.startsWith("state-"));
            StepResult.State state = support.state(step.id());
            if (state != null) {
                getStyleClass().add("state-" + state.name().toLowerCase());
                dot.getStyleClass().add("state-" + state.name().toLowerCase());
            }
            long millis = support.millis(step.id());
            time.setText(millis <= 0 ? "" : millis < 1000 ? millis + " ms" : String.format("%.1f s", millis / 1000.0));
            syncSelected();
        }
    }
}
