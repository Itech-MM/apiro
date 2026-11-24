package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.service.scenario.StepResult;
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
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;
import javafx.util.Duration;

final class DiagramView extends StackPane {

    private static final double NODE_WIDTH = 230;
    private static final double NODE_HEIGHT = 88;
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
    private final List<Link> links = new ArrayList<>();
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
        nodes.values().forEach(StepNode::dispose);
        links.forEach(Link::dispose);
        nodeLayer.getChildren().clear();
        edgeLayer.getChildren().clear();
        nodes.clear();
        links.clear();
        for (int i = 0; i < steps.size(); i++) {
            StepNode node = new StepNode(steps.get(i), i);
            node.setLayoutX(i * (NODE_WIDTH + GAP));
            node.setLayoutY(0);
            nodes.put(steps.get(i).id(), node);
            nodeLayer.getChildren().add(node);
            if (i > 0) {
                Link link = new Link(steps.get(i - 1).id(), steps.get(i).id(), i - 1, i);
                links.add(link);
                edgeLayer.getChildren().add(link.group);
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
        links.forEach(Link::refresh);
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

    private final class Link {

        final String fromId;
        final String toId;
        final CubicCurve curve;
        final Polygon arrow;
        final Group group;
        private final Timeline flow;

        Link(String fromId, String toId, int from, int to) {
            this.fromId = fromId;
            this.toId = toId;
            double x1 = from * (NODE_WIDTH + GAP) + NODE_WIDTH;
            double x2 = to * (NODE_WIDTH + GAP);
            double y = NODE_HEIGHT / 2;
            double handle = (x2 - x1) / 2;
            curve = new CubicCurve(x1, y, x1 + handle, y, x2 - handle, y, x2, y);
            curve.setFill(null);
            curve.getStyleClass().add("diagram-edge");
            arrow = new Polygon(x2, y, x2 - 10, y - 5.5, x2 - 10, y + 5.5);
            arrow.getStyleClass().add("diagram-arrow");
            group = new Group(curve, arrow);
            group.setMouseTransparent(true);
            flow = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(curve.strokeDashOffsetProperty(), 0)),
                    new KeyFrame(Duration.millis(450),
                            new KeyValue(curve.strokeDashOffsetProperty(), -14, Interpolator.LINEAR)));
            flow.setCycleCount(Animation.INDEFINITE);
            refresh();
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
        private final Label url = new Label();
        private final Label pill = new Label();
        private final Circle portIn = new Circle(5);
        private final Circle portOut = new Circle(5);
        private final FadeTransition pulse = new FadeTransition(Duration.millis(700), pill);

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
            HBox head = new HBox(8, number, name, spacer, time);
            head.setAlignment(Pos.CENTER_LEFT);
            head.getStyleClass().add("diagram-node-head");
            HBox.setHgrow(name, Priority.SOMETIMES);

            url.getStyleClass().add("diagram-node-url");
            url.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(url, Priority.ALWAYS);
            HBox line = new HBox(6, badge, url);
            line.setAlignment(Pos.CENTER_LEFT);
            pill.getStyleClass().add("diagram-pill");
            VBox body = new VBox(6, line, pill);
            body.getStyleClass().add("diagram-node-body");
            VBox card = new VBox(head, body);
            card.getStyleClass().add("diagram-card");
            card.setMinSize(NODE_WIDTH, NODE_HEIGHT);
            card.setMaxSize(NODE_WIDTH, NODE_HEIGHT);

            portIn.getStyleClass().add("diagram-port");
            portOut.getStyleClass().add("diagram-port");
            StackPane.setAlignment(portIn, Pos.CENTER_LEFT);
            StackPane.setAlignment(portOut, Pos.CENTER_RIGHT);
            portIn.setTranslateX(-5);
            portOut.setTranslateX(5);
            portIn.setMouseTransparent(true);
            portOut.setMouseTransparent(true);
            getChildren().addAll(card, portIn, portOut);

            pulse.setFromValue(1);
            pulse.setToValue(0.35);
            pulse.setAutoReverse(true);
            pulse.setCycleCount(Animation.INDEFINITE);

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
            if (state == StepResult.State.RUNNING) {
                pulse.play();
            } else {
                pulse.stop();
                pill.setOpacity(1);
            }
            long millis = support.millis(step.id());
            time.setText(millis <= 0 ? "" : millis < 1000 ? millis + " ms" : String.format("%.1f s", millis / 1000.0));
            syncSelected();
        }
    }
}
