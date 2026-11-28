package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_mm.apiro.model.Edge;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.service.scenario.ScenarioGraph;
import io.github.itech_mm.apiro.service.scenario.StepResult;
import io.github.itech_mm.apiro.service.scenario.StepSummary;
import io.github.itech_mm.apiro.service.scenario.VariableUsage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
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

        void selectEdge(String edgeId);
    }

    private static final double NODE_WIDTH = 230;
    private static final double NODE_HEIGHT = 100;
    private static final double GAP = 80;
    private static final double ROW_GAP = 70;
    private static final double GRAPH_GAP = 168;
    private static final double GRAPH_ROW_GAP = 92;
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
    private final CubicCurve wirePreview = new CubicCurve();
    private final Pane viewport = new Pane();
    private final Group world = new Group();
    private final Group edgeLayer = new Group();
    private final Group nodeLayer = new Group();
    private final Group dataLayer = new Group();
    private final Group badgeLayer = new Group();
    private final Affine view = new Affine();
    private final Map<String, StepNode> nodes = new LinkedHashMap<>();
    private final List<Link> links = new ArrayList<>();
    private final List<double[]> dataLabelBounds = new ArrayList<>();
    private final Set<String> cycleEdgeIds = new HashSet<>();
    private final Label empty = new Label("Add steps to see them here");
    private final Label validationBanner = new Label();
    private final Label zoomLabel = new Label("100%");
    private final HBox zoomTools;
    private final Button runButton = tool("▶ Run");
    private final Button stopButton = tool("■ Stop");
    private final ToggleButton logButton = new ToggleButton("Logs");
    private ScenarioItem scenario;
    private List<Step> steps = List.of();
    private boolean graphMode;
    private VariableUsage.Result usage = VariableUsage.Result.EMPTY;
    private String selectedId;
    private String selectedEdgeId;
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

        wirePreview.setFill(null);
        wirePreview.getStyleClass().add("diagram-wire-preview");
        wirePreview.setVisible(false);
        wirePreview.setMouseTransparent(true);

        getStyleClass().add("diagram-view");
        viewport.getStyleClass().add("diagram-viewport");
        viewport.setMinSize(0, 0);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(viewport.widthProperty());
        clip.heightProperty().bind(viewport.heightProperty());
        viewport.setClip(clip);
        dataLayer.setMouseTransparent(true);
        world.getChildren().addAll(edgeLayer, nodeLayer, dataLayer, badgeLayer, marker, wirePreview);
        world.getTransforms().add(view);
        viewport.getChildren().add(world);

        empty.getStyleClass().add("muted");
        empty.setMouseTransparent(true);

        validationBanner.getStyleClass().add("diagram-validation-banner");
        validationBanner.setVisible(false);
        validationBanner.setManaged(false);
        validationBanner.setMouseTransparent(true);
        StackPane.setAlignment(validationBanner, Pos.BOTTOM_CENTER);
        StackPane.setMargin(validationBanner, new Insets(12));

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

        getChildren().addAll(viewport, empty, validationBanner, tools, runTools);

        setOnKeyPressed(event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.Z && !event.isShiftDown()) {
                actions.undo();
                event.consume();
            } else if (event.isShortcutDown() && (event.getCode() == KeyCode.Y
                    || event.getCode() == KeyCode.Z && event.isShiftDown())) {
                actions.redo();
                event.consume();
            } else if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                if (this.selectedEdgeId != null) {
                    support.removeEdge(this.selectedEdgeId);
                    event.consume();
                } else if (selectedId != null) {
                    support.remove(selectedId);
                    event.consume();
                }
            }
        });
        viewport.setOnDragOver(event -> {
            Dragboard board = event.getDragboard();
            if (board.hasContent(StepCell.STEP) || actions.dropped(board).isPresent()) {
                event.acceptTransferModes(TransferMode.MOVE);
                if (!graphMode) {
                    showMarker(gapAt(event.getSceneX(), event.getSceneY()));
                }
                event.consume();
            }
        });
        viewport.setOnDragExited(event -> marker.setVisible(false));
        viewport.setOnDragDropped(event -> {
            Dragboard board = event.getDragboard();
            int gap = graphMode ? steps.size() : gapAt(event.getSceneX(), event.getSceneY());
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

    void show(ScenarioItem nextScenario, String selectedStep, String selectedEdge, boolean refit,
              VariableUsage.Result analysis) {
        scenario = nextScenario;
        steps = nextScenario == null ? List.of() : nextScenario.steps();
        usage = analysis == null ? VariableUsage.Result.EMPTY : analysis;
        selectedId = selectedStep;
        selectedEdgeId = selectedEdge;
        nodes.values().forEach(StepNode::dispose);
        links.forEach(Link::dispose);
        nodeLayer.getChildren().clear();
        edgeLayer.getChildren().clear();
        badgeLayer.getChildren().clear();
        nodes.clear();
        links.clear();
        cycleEdgeIds.clear();

        ScenarioGraph.Plan plan = nextScenario == null || steps.isEmpty() ? null : ScenarioGraph.plan(nextScenario);
        graphMode = nextScenario != null && !nextScenario.edges().isEmpty()
                && (plan == null || !plan.valid() || plan.isBranched());

        if (plan != null && !plan.valid()) {
            collectCycleEdges(nextScenario, cycleEdgeIds);
            String problem = cycleEdgeIds.isEmpty() ? plan.problem()
                    : plan.problem() + " — right-click a red edge to remove it";
            validationBanner.setText(problem);
            validationBanner.setVisible(true);
            validationBanner.setManaged(true);
        } else {
            List<String> warnings = nextScenario == null ? List.of() : ScenarioGraph.warnings(nextScenario);
            validationBanner.setText(warnings.isEmpty() ? "" : "Warning: " + String.join(" | ", warnings));
            validationBanner.setVisible(!warnings.isEmpty());
            validationBanner.setManaged(!warnings.isEmpty());
        }

        for (int i = 0; i < steps.size(); i++) {
            StepNode node = new StepNode(steps.get(i), i);
            nodes.put(steps.get(i).id(), node);
            nodeLayer.getChildren().add(node);
        }

        if (nextScenario == null || nextScenario.edges().isEmpty()) {
            for (int i = 1; i < steps.size(); i++) {
                Link link = new Link(null, steps.get(i - 1).id(), steps.get(i).id(), i - 1, 0, 1, 0, 1, 0, 1);
                links.add(link);
                edgeLayer.getChildren().add(link.group);
                badgeLayer.getChildren().add(link.badge);
            }
        } else {
            Map<String, List<Edge>> byFrom = new HashMap<>();
            Map<String, List<Edge>> byTo = new HashMap<>();
            Map<String, List<Edge>> byPair = new HashMap<>();
            for (Edge edge : nextScenario.edges()) {
                byFrom.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
                byTo.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge);
                byPair.computeIfAbsent(edge.from() + "->" + edge.to(), k -> new ArrayList<>()).add(edge);
            }
            for (Edge edge : nextScenario.edges()) {
                List<Edge> outList = byFrom.getOrDefault(edge.from(), List.of());
                List<Edge> inList = byTo.getOrDefault(edge.to(), List.of());
                List<Edge> pairList = byPair.getOrDefault(edge.from() + "->" + edge.to(), List.of());
                int priorityIdx = outList.indexOf(edge);
                int inIdx = inList.indexOf(edge);
                int pairIdx = pairList.indexOf(edge);
                Link link = new Link(edge, edge.from(), edge.to(), indexOf(edge.from()),
                        Math.max(0, priorityIdx), Math.max(1, outList.size()),
                        Math.max(0, inIdx), Math.max(1, inList.size()),
                        Math.max(0, pairIdx), Math.max(1, pairList.size()));
                links.add(link);
                edgeLayer.getChildren().add(link.group);
                badgeLayer.getChildren().add(link.badge);
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

    private static void collectCycleEdges(ScenarioItem scenario, Set<String> out) {
        if (scenario == null || scenario.edges().isEmpty()) {
            return;
        }
        Map<String, List<Edge>> outgoing = new HashMap<>();
        for (Edge edge : scenario.edges()) {
            outgoing.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
        }
        Map<String, Integer> state = new HashMap<>();
        for (Step step : scenario.steps()) {
            if (state.getOrDefault(step.id(), 0) == 0) {
                dfsCycle(step.id(), outgoing, state, out);
            }
        }
    }

    private static void dfsCycle(String nodeId, Map<String, List<Edge>> outgoing,
                                 Map<String, Integer> state, Set<String> cycleEdges) {
        state.put(nodeId, 1);
        for (Edge edge : outgoing.getOrDefault(nodeId, List.of())) {
            int targetState = state.getOrDefault(edge.to(), 0);
            if (edge.from().equals(edge.to()) || targetState == 1) {
                cycleEdges.add(edge.id());
            } else if (targetState == 0) {
                dfsCycle(edge.to(), outgoing, state, cycleEdges);
            }
        }
        state.put(nodeId, 2);
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
        if (graphMode && scenario != null) {
            DiagramLayout.GraphLayout layout = DiagramLayout.graphLayout(scenario, NODE_WIDTH, NODE_HEIGHT,
                    GRAPH_GAP, GRAPH_ROW_GAP);
            for (StepNode node : nodes.values()) {
                DiagramLayout.Point point = layout.positions().get(node.step.id());
                double x = point == null ? 0 : point.x();
                double y = point == null ? 0 : point.y();
                node.setLayoutX(x);
                node.setLayoutY(y);
                node.placeGraphPorts();
            }
        } else {
            int index = 0;
            for (StepNode node : nodes.values()) {
                node.setLayoutX(DiagramLayout.nodeX(index, cols, NODE_WIDTH, GAP));
                node.setLayoutY(DiagramLayout.nodeY(index, cols, NODE_HEIGHT, ROW_GAP));
                node.placePorts(index, steps.size());
                index++;
            }
        }
        refreshData();
        int skipDetour = 0;
        int backDetour = 0;
        for (Link link : links) {
            if (graphMode) {
                StepNode fn = nodes.get(link.fromId);
                StepNode tn = nodes.get(link.toId);
                if (fn != null && tn != null) {
                    double fx = fn.getLayoutX();
                    double tx = tn.getLayoutX();
                    double fy = fn.getLayoutY();
                    double ty = tn.getLayoutY();
                    boolean sameColDown = Math.abs(fx - tx) < NODE_WIDTH * 0.5 && ty > fy + NODE_HEIGHT * 0.5;
                    if (!sameColDown && tx < fx + NODE_WIDTH * 0.5) {
                        link.place(backDetour++);
                        continue;
                    }
                    if (!sameColDown && tx > fx + NODE_WIDTH + 20 && hasBlockingNode(fn, tn)) {
                        link.place(skipDetour++);
                        continue;
                    }
                }
            }
            link.place(0);
        }
        resolveBadgeOverlaps();
        Platform.runLater(this::resolveBadgeOverlaps);
    }

    private boolean hasBlockingNode(StepNode fn, StepNode tn) {
        double fx = fn.getLayoutX();
        double fy = fn.getLayoutY();
        double tx = tn.getLayoutX();
        double ty = tn.getLayoutY();
        double x1 = fx + NODE_WIDTH;
        double y1 = fy + NODE_HEIGHT / 2.0;
        double x2 = tx;
        double y2 = ty + NODE_HEIGHT / 2.0;
        for (StepNode mid : nodes.values()) {
            if (mid == fn || mid == tn) {
                continue;
            }
            double mx = mid.getLayoutX();
            double my = mid.getLayoutY();
            if (mx + NODE_WIDTH > fx + NODE_WIDTH + 8 && mx < tx - 8) {
                double t = (mx + NODE_WIDTH / 2.0 - x1) / Math.max(1.0, x2 - x1);
                double chordY = y1 + (y2 - y1) * Math.max(0.0, Math.min(1.0, t));
                if (chordY >= my - 26 && chordY <= my + NODE_HEIGHT + 26) {
                    return true;
                }
            }
        }
        return false;
    }

    private void resolveBadgeOverlaps() {
        List<double[]> occupied = new ArrayList<>();
        for (StepNode node : nodes.values()) {
            occupied.add(new double[] {
                    node.getLayoutX() - 8,
                    node.getLayoutY() - 8,
                    NODE_WIDTH + 16,
                    NODE_HEIGHT + 16
            });
        }
        occupied.addAll(dataLabelBounds);
        for (Link link : links) {
            if (!link.badge.isVisible()) {
                continue;
            }
            double w = link.badgeWidth();
            double h = link.badgeHeight();
            double baseX = link.labelX - w / 2.0;
            double baseY = link.labelY - h / 2.0;
            double chosenY = baseY;
            for (int step = 0; step < 12; step++) {
                double delta = step == 0 ? 0 : ((step + 1) / 2) * 26.0 * (step % 2 == 1 ? 1 : -1);
                double candY = baseY + delta;
                if (!overlapsAny(baseX, candY, w, h, occupied)) {
                    chosenY = candY;
                    break;
                }
            }
            link.badge.setLayoutX(baseX);
            link.badge.setLayoutY(chosenY);
            occupied.add(new double[] { baseX - 4, chosenY - 4, w + 8, h + 8 });
        }
    }

    private static boolean overlapsAny(double x, double y, double w, double h, List<double[]> rects) {
        for (double[] r : rects) {
            if (x < r[0] + r[2] && x + w > r[0] && y < r[1] + r[3] && y + h > r[1]) {
                return true;
            }
        }
        return false;
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

    void select(String stepId, String edgeId) {
        selectedId = stepId;
        selectedEdgeId = edgeId;
        nodes.values().forEach(StepNode::syncSelected);
        links.forEach(Link::refresh);
        refreshData();
    }

    void setUsage(VariableUsage.Result analysis) {
        usage = analysis == null ? VariableUsage.Result.EMPTY : analysis;
        refresh();
    }

    void refresh() {
        nodes.values().forEach(StepNode::refresh);
        links.forEach(Link::refresh);
        refreshData();
        resolveBadgeOverlaps();
    }

    private void refreshData() {
        dataLayer.getChildren().clear();
        dataLabelBounds.clear();
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
        StepNode fromNode = nodes.get(link.fromStepId());
        StepNode toNode = nodes.get(link.toStepId());
        if (fromNode == null || toNode == null) {
            return;
        }
        double fx = fromNode.getLayoutX();
        double fy = fromNode.getLayoutY();
        double tx = toNode.getLayoutX();
        double ty = toNode.getLayoutY();
        boolean sameRow = Math.abs(fy - ty) < 1.0;
        double shift = sameRow ? 0 : 36;
        boolean arcAbove = sameRow && graphMode;
        double x1 = fx + NODE_WIDTH / 2 + shift;
        double y1 = arcAbove ? fy : (sameRow || fy < ty ? fy + NODE_HEIGHT : fy);
        double x2 = tx + NODE_WIDTH / 2 + shift;
        double y2 = arcAbove ? ty : (sameRow ? ty + NODE_HEIGHT : (fy < ty ? ty : ty + NODE_HEIGHT));
        double c1y;
        double c2y;
        double uy;
        if (arcAbove) {
            double lift = 46 + Math.min(60, Math.abs(x2 - x1) * 0.12);
            c1y = y1 - lift;
            c2y = y2 - lift;
            uy = 1;
        } else if (sameRow) {
            double drop = 46 + Math.min(60, Math.abs(x2 - x1) * 0.12);
            c1y = y1 + drop;
            c2y = y2 + drop;
            uy = -1;
        } else {
            double dy = y2 - y1;
            double handle = Math.max(30, Math.abs(dy) / 2);
            c1y = y1 + (dy >= 0 ? handle : -handle);
            c2y = y2 - (dy >= 0 ? handle : -handle);
            uy = dy >= 0 ? 1 : -1;
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
        double lx = midX - width / 2;
        double ly = midY - 9;
        label.setLayoutX(lx);
        label.setLayoutY(ly);
        dataLabelBounds.add(new double[] { lx - 4, ly - 4, width + 8, 26 });
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
        double minX = 0;
        double minY = 0;
        double contentWidth;
        double contentHeight;
        if (graphMode && scenario != null) {
            DiagramLayout.GraphLayout layout = DiagramLayout.graphLayout(scenario, NODE_WIDTH, NODE_HEIGHT,
                    GRAPH_GAP, GRAPH_ROW_GAP);
            double maxX = layout.width();
            double maxY = layout.height();
            for (Link link : links) {
                minY = Math.min(minY, Math.min(link.labelY - 24, link.curveMinY()));
                maxY = Math.max(maxY, Math.max(link.labelY + 24, link.curveMaxY()));
            }
            for (double[] rect : dataLabelBounds) {
                minY = Math.min(minY, rect[1]);
                maxY = Math.max(maxY, rect[1] + rect[3]);
            }
            contentWidth = Math.max(NODE_WIDTH, maxX - minX);
            contentHeight = Math.max(NODE_HEIGHT, maxY - minY);
        } else {
            int columns = DiagramLayout.columns(width, TARGET_ZOOM, MARGIN, NODE_WIDTH, GAP, MIN_COLUMNS, MAX_COLUMNS);
            if (columns != cols) {
                cols = columns;
                relayout();
            }
            contentWidth = DiagramLayout.contentWidth(steps.size(), cols, NODE_WIDTH, GAP);
            contentHeight = DiagramLayout.contentHeight(steps.size(), cols, NODE_HEIGHT, ROW_GAP);
        }
        double fitted = Math.min((width - 2 * MARGIN) / contentWidth, (height - 2 * MARGIN) / contentHeight);
        zoom = clamp(Math.max(Math.min(fitted, 1), floor));
        double scaledWidth = contentWidth * zoom;
        double scaledHeight = contentHeight * zoom;
        panX = (scaledWidth + 2 * MARGIN <= width ? (width - scaledWidth) / 2 : MARGIN) - minX * zoom;
        panY = (scaledHeight + 2 * MARGIN <= height ? (height - scaledHeight) / 2 : MARGIN) - minY * zoom;
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

    private StepNode nodeAtWorld(double worldX, double worldY, String excludeStepId) {
        for (StepNode node : nodes.values()) {
            if (node.step.id().equals(excludeStepId)) {
                continue;
            }
            double x = node.getLayoutX();
            double y = node.getLayoutY();
            if (worldX >= x - 8 && worldX <= x + NODE_WIDTH + 8
                    && worldY >= y - 8 && worldY <= y + NODE_HEIGHT + 8) {
                return node;
            }
        }
        return null;
    }

    private final class Link {

        final Edge edge;
        final String fromId;
        final String toId;
        final CubicCurve hitCurve;
        final CubicCurve curve;
        final Polygon arrow;
        final Label badge;
        final Tooltip badgeTip = new Tooltip();
        final Group group;
        private final Timeline flow;
        private final int from;
        private final int priorityIndex;
        private final int outgoingCount;
        private final int incomingIndex;
        private final int incomingCount;
        private final int pairIndex;
        private final int pairTotal;
        private double labelX;
        private double labelY;

        Link(Edge edge, String fromId, String toId, int from, int priorityIndex, int outgoingCount,
             int incomingIndex, int incomingCount, int pairIndex, int pairTotal) {
            this.edge = edge;
            this.fromId = fromId;
            this.toId = toId;
            this.from = from;
            this.priorityIndex = priorityIndex;
            this.outgoingCount = outgoingCount;
            this.incomingIndex = incomingIndex;
            this.incomingCount = incomingCount;
            this.pairIndex = pairIndex;
            this.pairTotal = pairTotal;

            hitCurve = new CubicCurve();
            hitCurve.setFill(null);
            hitCurve.getStyleClass().add("diagram-edge-hit");

            curve = new CubicCurve();
            curve.setFill(null);
            curve.getStyleClass().add("diagram-edge");
            curve.setMouseTransparent(true);

            arrow = new Polygon();
            arrow.getStyleClass().add("diagram-arrow");
            arrow.setMouseTransparent(true);

            badge = new Label();
            badge.getStyleClass().add("diagram-edge-label");
            badge.widthProperty().addListener((obs, oldV, newV) -> resolveBadgeOverlaps());
            badge.heightProperty().addListener((obs, oldV, newV) -> resolveBadgeOverlaps());

            group = new Group(hitCurve, curve, arrow);

            flow = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(curve.strokeDashOffsetProperty(), 0)),
                    new KeyFrame(Duration.millis(450),
                            new KeyValue(curve.strokeDashOffsetProperty(), -14, Interpolator.LINEAR)));
            flow.setCycleCount(Animation.INDEFINITE);

            boolean interactive = edge != null && !edge.id().isBlank();
            hitCurve.setMouseTransparent(!interactive);
            badge.setMouseTransparent(!interactive);
            if (interactive) {
                MenuItem editItem = new MenuItem("Edit branch condition");
                editItem.setOnAction(e -> {
                    actions.selectEdge(edge.id());
                    actions.focusInspector();
                });
                MenuItem upItem = new MenuItem("Move priority up");
                upItem.setDisable(priorityIndex <= 0);
                upItem.setOnAction(e -> support.moveEdge(fromId, edge.id(), priorityIndex - 1));
                MenuItem downItem = new MenuItem("Move priority down");
                downItem.setDisable(priorityIndex >= outgoingCount - 1);
                downItem.setOnAction(e -> support.moveEdge(fromId, edge.id(), priorityIndex + 1));
                MenuItem removeItem = new MenuItem("Remove branch");
                removeItem.setOnAction(e -> support.removeEdge(edge.id()));
                ContextMenu edgeMenu = new ContextMenu(editItem, upItem, downItem, removeItem);

                Runnable selectAction = () -> {
                    DiagramView.this.requestFocus();
                    actions.selectEdge(edge.id());
                };
                hitCurve.setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.PRIMARY) {
                        selectAction.run();
                        if (e.getClickCount() == 2) {
                            actions.focusInspector();
                        }
                        e.consume();
                    }
                });
                badge.setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.PRIMARY) {
                        selectAction.run();
                        if (e.getClickCount() == 2) {
                            actions.focusInspector();
                        }
                        e.consume();
                    }
                });
                hitCurve.setOnContextMenuRequested(e -> {
                    selectAction.run();
                    edgeMenu.show(hitCurve, e.getScreenX(), e.getScreenY());
                    e.consume();
                });
                badge.setOnContextMenuRequested(e -> {
                    selectAction.run();
                    edgeMenu.show(badge, e.getScreenX(), e.getScreenY());
                    e.consume();
                });
            }

            place(0);
            refresh();
        }

        double badgeWidth() {
            double w = badge.getWidth();
            if (w > 1) {
                return w;
            }
            return Math.min(148.0, Math.max(36.0, badge.getText().length() * 6.8 + 16.0));
        }

        double badgeHeight() {
            double h = badge.getHeight();
            return h > 1 ? h : 22.0;
        }

        double curveMinY() {
            return Math.min(Math.min(curve.getStartY(), curve.getEndY()),
                    Math.min(curve.getControlY1(), curve.getControlY2()));
        }

        double curveMaxY() {
            return Math.max(Math.max(curve.getStartY(), curve.getEndY()),
                    Math.max(curve.getControlY1(), curve.getControlY2()));
        }

        void place(int detourIndex) {
            double x1;
            double y1;
            double x2;
            double y2;
            double cx1;
            double cy1;
            double cx2;
            double cy2;
            double ux;
            double uy;
            if (graphMode) {
                StepNode fn = nodes.get(fromId);
                StepNode tn = nodes.get(toId);
                double fx = fn == null ? 0 : fn.getLayoutX();
                double fy = fn == null ? 0 : fn.getLayoutY();
                double tx = tn == null ? 0 : tn.getLayoutX();
                double ty = tn == null ? 0 : tn.getLayoutY();
                double outOffset = Math.max(-28, Math.min(28, (priorityIndex - (outgoingCount - 1) / 2.0) * 14.0));
                double inOffset = Math.max(-28, Math.min(28, (incomingIndex - (incomingCount - 1) / 2.0) * 14.0));
                double spread = (pairIndex - (pairTotal - 1) / 2.0) * 34.0;
                boolean sameColDown = Math.abs(fx - tx) < NODE_WIDTH * 0.5 && ty > fy + NODE_HEIGHT * 0.5;
                if (sameColDown) {
                    x1 = fx + NODE_WIDTH / 2.0 + outOffset;
                    y1 = fy + NODE_HEIGHT;
                    x2 = tx + NODE_WIDTH / 2.0 + inOffset;
                    y2 = ty;
                    double handle = Math.max(30, (y2 - y1) * 0.45);
                    cx1 = x1 + spread;
                    cy1 = y1 + handle;
                    cx2 = x2 + spread;
                    cy2 = y2 - handle;
                    ux = 0;
                    uy = 1;
                } else if (tx >= fx + NODE_WIDTH * 0.5) {
                    x1 = fx + NODE_WIDTH;
                    y1 = fy + NODE_HEIGHT / 2.0 + outOffset;
                    x2 = tx;
                    y2 = ty + NODE_HEIGHT / 2.0 + inOffset;
                    double minBlockY = Double.POSITIVE_INFINITY;
                    double maxBlockBottom = Double.NEGATIVE_INFINITY;
                    boolean blocked = false;
                    for (StepNode mid : nodes.values()) {
                        if (mid == fn || mid == tn) {
                            continue;
                        }
                        double mx = mid.getLayoutX();
                        double my = mid.getLayoutY();
                        if (mx + NODE_WIDTH > fx + NODE_WIDTH + 8 && mx < tx - 8) {
                            double t = (mx + NODE_WIDTH / 2.0 - x1) / Math.max(1.0, x2 - x1);
                            double chordY = y1 + (y2 - y1) * Math.max(0.0, Math.min(1.0, t));
                            if (chordY >= my - 26 && chordY <= my + NODE_HEIGHT + 26) {
                                blocked = true;
                                minBlockY = Math.min(minBlockY, my);
                                maxBlockBottom = Math.max(maxBlockBottom, my + NODE_HEIGHT);
                            }
                        }
                    }
                    if (blocked) {
                        double midChordY = (y1 + y2) / 2.0;
                        double belowY = maxBlockBottom + 46 + detourIndex * 34;
                        double aboveY = minBlockY - 46 - detourIndex * 34;
                        boolean preferBelow = (edge != null && (edge.condition().isElse() || priorityIndex > 0))
                                || Math.abs(belowY - midChordY) <= Math.abs(aboveY - midChordY) + 12;
                        double targetMidY = preferBelow ? belowY : aboveY;
                        double cy = (targetMidY - 0.25 * midChordY) / 0.75 + spread * 0.5;
                        cx1 = x1 + (x2 - x1) * 0.28;
                        cx2 = x2 - (x2 - x1) * 0.28;
                        cy1 = cy;
                        cy2 = cy;
                    } else {
                        double handle = Math.max(55, (x2 - x1) * 0.44);
                        cx1 = x1 + handle;
                        cy1 = y1 + spread;
                        cx2 = x2 - handle;
                        cy2 = y2 + spread;
                    }
                    ux = 1;
                    uy = 0;
                } else {
                    x1 = fx + NODE_WIDTH / 2.0 + outOffset * 0.5;
                    y1 = fy;
                    x2 = tx + NODE_WIDTH / 2.0 + inOffset * 0.5;
                    y2 = ty;
                    double minX = Math.min(fx, tx) - 20;
                    double maxX = Math.max(fx, tx) + NODE_WIDTH + 20;
                    double minTop = Math.min(fy, ty);
                    for (StepNode node : nodes.values()) {
                        double nx = node.getLayoutX();
                        if (nx + NODE_WIDTH > minX && nx < maxX) {
                            minTop = Math.min(minTop, node.getLayoutY());
                        }
                    }
                    double targetMidY = minTop - (56 + detourIndex * 34 + Math.abs(spread) * 0.5);
                    double cy = (targetMidY - 0.25 * ((y1 + y2) / 2.0)) / 0.75;
                    cx1 = x1;
                    cy1 = cy;
                    cx2 = x2;
                    cy2 = cy;
                    ux = 0;
                    uy = 1;
                }
            } else {
                int to = from + 1;
                double fx = DiagramLayout.nodeX(from, cols, NODE_WIDTH, GAP);
                double fy = DiagramLayout.nodeY(from, cols, NODE_HEIGHT, ROW_GAP);
                double tx = DiagramLayout.nodeX(to, cols, NODE_WIDTH, GAP);
                double ty = DiagramLayout.nodeY(to, cols, NODE_HEIGHT, ROW_GAP);
                if (DiagramLayout.turns(from, cols)) {
                    x1 = fx + NODE_WIDTH / 2;
                    y1 = fy + NODE_HEIGHT;
                    x2 = tx + NODE_WIDTH / 2;
                    y2 = ty;
                    double handle = (y2 - y1) / 2;
                    cx1 = x1;
                    cy1 = y1 + handle;
                    cx2 = x2;
                    cy2 = y2 - handle;
                    ux = 0;
                    uy = 1;
                } else {
                    int direction = DiagramLayout.direction(from, cols);
                    x1 = direction > 0 ? fx + NODE_WIDTH : fx;
                    x2 = direction > 0 ? tx : tx + NODE_WIDTH;
                    y1 = fy + NODE_HEIGHT / 2;
                    y2 = y1;
                    double handle = (x2 - x1) / 2;
                    cx1 = x1 + handle;
                    cy1 = y1;
                    cx2 = x2 - handle;
                    cy2 = y2;
                    ux = direction;
                    uy = 0;
                }
            }
            curve.setStartX(x1);
            curve.setStartY(y1);
            curve.setControlX1(cx1);
            curve.setControlY1(cy1);
            curve.setControlX2(cx2);
            curve.setControlY2(cy2);
            curve.setEndX(x2);
            curve.setEndY(y2);

            hitCurve.setStartX(x1);
            hitCurve.setStartY(y1);
            hitCurve.setControlX1(cx1);
            hitCurve.setControlY1(cy1);
            hitCurve.setControlX2(cx2);
            hitCurve.setControlY2(cy2);
            hitCurve.setEndX(x2);
            hitCurve.setEndY(y2);

            double baseX = x2 - ux * 10;
            double baseY = y2 - uy * 10;
            double px = -uy * 5.5;
            double py = ux * 5.5;
            arrow.getPoints().setAll(x2, y2, baseX + px, baseY + py, baseX - px, baseY - py);

            labelX = 0.125 * x1 + 0.375 * cx1 + 0.375 * cx2 + 0.125 * x2;
            labelY = 0.125 * y1 + 0.375 * cy1 + 0.375 * cy2 + 0.125 * y2;
            badge.setLayoutX(labelX - badgeWidth() / 2.0);
            badge.setLayoutY(labelY - badgeHeight() / 2.0);
        }

        void refresh() {
            StepResult.State source = support.state(fromId);
            StepResult.State target = support.state(toId);
            String takenEdge = support.takenEdgeId(fromId);
            boolean cycleEdge = edge != null && cycleEdgeIds.contains(edge.id());
            String kind = "idle";
            if (cycleEdge) {
                kind = "failed";
            } else if (target == StepResult.State.RUNNING) {
                kind = "active";
            } else if (edge != null && !edge.id().isBlank() && !takenEdge.isBlank()) {
                if (edge.id().equals(takenEdge)) {
                    kind = target == StepResult.State.FAILED ? "failed" : "done";
                } else {
                    kind = "dim";
                }
            } else if (source == StepResult.State.FAILED) {
                kind = "failed";
            } else if (source == StepResult.State.PASSED
                    && (target == StepResult.State.PASSED || target == StepResult.State.FAILED)) {
                kind = "done";
            } else if (source == StepResult.State.CANCELLED || source == StepResult.State.SKIPPED
                    || target == StepResult.State.SKIPPED || target == StepResult.State.CANCELLED) {
                kind = "dim";
            }
            boolean selected = edge != null && !edge.id().isBlank() && edge.id().equals(selectedEdgeId);

            curve.getStyleClass().removeIf(style -> style.startsWith("edge-"));
            arrow.getStyleClass().removeIf(style -> style.startsWith("edge-"));
            badge.getStyleClass().removeIf(style -> style.startsWith("edge-"));
            badge.getStyleClass().removeAll("selected", "warn");

            curve.getStyleClass().add("edge-" + kind);
            arrow.getStyleClass().add("edge-" + kind);
            badge.getStyleClass().add("edge-" + kind);
            if (selected) {
                curve.getStyleClass().add("edge-selected");
                arrow.getStyleClass().add("edge-selected");
                badge.getStyleClass().add("selected");
            }
            if (kind.equals("active")) {
                curve.getStrokeDashArray().setAll(8.0, 6.0);
                flow.play();
            } else {
                flow.stop();
                curve.setStrokeDashOffset(0);
                if (kind.equals("dim")) {
                    curve.getStrokeDashArray().setAll(3.0, 6.0);
                } else if (cycleEdge || (edge != null && edge.condition().isElse())) {
                    curve.getStrokeDashArray().setAll(6.0, 4.0);
                } else {
                    curve.getStrokeDashArray().clear();
                }
            }

            boolean showLabel = edge != null && (!edge.displayLabel().isBlank() || outgoingCount > 1 || cycleEdge);
            badge.setVisible(showLabel);
            badge.setManaged(showLabel);
            if (showLabel) {
                String raw = edge.displayLabel().isBlank() ? "always" : edge.displayLabel();
                String prefix = (cycleEdge ? "loop • " : "") + (outgoingCount > 1 ? "#" + (priorityIndex + 1) + " " : "");
                String full = prefix + raw;
                String shown = full.length() > 26 ? full.substring(0, 25) + "…" : full;
                badge.setText(shown);
                List<String> missingVars = edge.id().isBlank() ? List.of() : usage.unresolvedForEdge(edge.id());
                if (cycleEdge) {
                    badgeTip.setText(full + "\nForms a loop — right-click and choose Remove branch");
                } else if (!missingVars.isEmpty()) {
                    badge.getStyleClass().add("warn");
                    badgeTip.setText(full + "\nUndefined variable(s): " + String.join(", ", missingVars));
                } else {
                    badgeTip.setText(full);
                }
                Tooltip.install(badge, badgeTip);
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
        private final Circle portOut = new Circle(5.5);
        private final FadeTransition pulse = new FadeTransition(Duration.millis(700), pill);
        private final Tooltip tip = new Tooltip();
        private boolean tipInstalled;
        private StepNode hoveredDropTarget;
        private boolean hoveredDropValid;

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
            portOut.getStyleClass().addAll("diagram-port", "diagram-port-out");
            portIn.setMouseTransparent(true);
            portOut.setMouseTransparent(false);
            Tooltip.install(portOut, new Tooltip("Drag to a downstream step to add branch"));
            getChildren().addAll(card, portIn, portOut);

            pulse.setFromValue(1);
            pulse.setToValue(0.35);
            pulse.setAutoReverse(true);
            pulse.setCycleCount(Animation.INDEFINITE);

            setupPortWiring();

            Menu branchTo = new Menu("Add branch to");
            MenuItem left = new MenuItem("Move left");
            left.setOnAction(event -> support.move(step.id(), indexOf(step.id()) - 1));
            MenuItem right = new MenuItem("Move right");
            right.setOnAction(event -> support.move(step.id(), indexOf(step.id()) + 1));
            MenuItem remove = new MenuItem("Remove step");
            remove.setOnAction(event -> support.remove(step.id()));
            ContextMenu menu = new ContextMenu(branchTo, left, right, remove);
            menu.setOnShowing(event -> {
                branchTo.getItems().clear();
                for (int i = 0; i < steps.size(); i++) {
                    Step target = steps.get(i);
                    if (!target.id().equals(step.id())) {
                        String title = target.name().isBlank() ? target.requestId() : target.name();
                        boolean allowed = support.canBranch(step.id(), target.id());
                        MenuItem item = new MenuItem((i + 1) + ". " + title + (allowed ? "" : " (loop)"));
                        item.setDisable(!allowed);
                        item.setOnAction(e -> support.addBranch(step.id(), target.id()));
                        branchTo.getItems().add(item);
                    }
                }
                branchTo.setDisable(branchTo.getItems().isEmpty());
                left.setVisible(!graphMode);
                right.setVisible(!graphMode);
            });
            setOnContextMenuRequested(event -> {
                selectedId = step.id();
                selectedEdgeId = null;
                nodes.values().forEach(StepNode::syncSelected);
                links.forEach(Link::refresh);
                onSelect.accept(step.id());
                menu.show(this, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            setOnDragDetected(event -> {
                if (graphMode) {
                    return;
                }
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
                    selectedEdgeId = null;
                    nodes.values().forEach(StepNode::syncSelected);
                    links.forEach(Link::refresh);
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

        private void setupPortWiring() {
            portOut.setOnMousePressed(event -> {
                if (event.getButton() != MouseButton.PRIMARY) {
                    return;
                }
                DiagramView.this.requestFocus();
                Point2D start = portOutWorldCenter();
                wirePreview.getStyleClass().remove("wire-invalid");
                wirePreview.setStartX(start.getX());
                wirePreview.setStartY(start.getY());
                wirePreview.setEndX(start.getX());
                wirePreview.setEndY(start.getY());
                wirePreview.setControlX1(start.getX() + 40);
                wirePreview.setControlY1(start.getY());
                wirePreview.setControlX2(start.getX() - 40);
                wirePreview.setControlY2(start.getY());
                wirePreview.setVisible(true);
                hoveredDropTarget = null;
                hoveredDropValid = false;
                event.consume();
            });
            portOut.setOnMouseDragged(event -> {
                if (!wirePreview.isVisible()) {
                    return;
                }
                Point2D local = viewport.sceneToLocal(event.getSceneX(), event.getSceneY());
                double wx = (local.getX() - panX) / zoom;
                double wy = (local.getY() - panY) / zoom;
                double sx = wirePreview.getStartX();
                double sy = wirePreview.getStartY();
                double handle = Math.max(40, Math.abs(wx - sx) * 0.45);
                wirePreview.setEndX(wx);
                wirePreview.setEndY(wy);
                wirePreview.setControlX1(sx + handle);
                wirePreview.setControlY1(sy);
                wirePreview.setControlX2(wx - handle);
                wirePreview.setControlY2(wy);

                StepNode candidate = nodeAtWorld(wx, wy, step.id());
                boolean valid = candidate != null && support.canBranch(step.id(), candidate.step.id());
                if (hoveredDropTarget != candidate || hoveredDropValid != valid) {
                    if (hoveredDropTarget != null) {
                        hoveredDropTarget.getStyleClass().remove("drop-target");
                    }
                    hoveredDropTarget = candidate;
                    hoveredDropValid = valid;
                    wirePreview.getStyleClass().remove("wire-invalid");
                    if (candidate != null) {
                        if (valid) {
                            candidate.getStyleClass().add("drop-target");
                        } else {
                            wirePreview.getStyleClass().add("wire-invalid");
                        }
                    }
                }
                event.consume();
            });
            portOut.setOnMouseReleased(event -> {
                if (!wirePreview.isVisible()) {
                    return;
                }
                wirePreview.setVisible(false);
                wirePreview.getStyleClass().remove("wire-invalid");
                if (hoveredDropTarget != null) {
                    String targetId = hoveredDropTarget.step.id();
                    hoveredDropTarget.getStyleClass().remove("drop-target");
                    boolean valid = hoveredDropValid;
                    hoveredDropTarget = null;
                    hoveredDropValid = false;
                    if (valid) {
                        support.addBranch(step.id(), targetId);
                    }
                }
                event.consume();
            });
        }

        private Point2D portOutWorldCenter() {
            Pos side = StackPane.getAlignment(portOut);
            double cx = getLayoutX() + (side == Pos.CENTER_LEFT ? 0 : side == Pos.CENTER_RIGHT ? NODE_WIDTH : NODE_WIDTH / 2.0);
            double cy = getLayoutY() + (side == Pos.BOTTOM_CENTER ? NODE_HEIGHT : side == Pos.TOP_CENTER ? 0 : NODE_HEIGHT / 2.0);
            return new Point2D(cx, cy);
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

        void placeGraphPorts() {
            place(portIn, Pos.CENTER_LEFT);
            place(portOut, Pos.CENTER_RIGHT);
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
            if (step.id().equals(selectedId) && selectedEdgeId == null) {
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
                String effectiveUrl = !step.urlOverride().isBlank() ? step.urlOverride()
                        : (request.get().url() == null ? "" : request.get().url());
                url.setText(effectiveUrl);
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
            List<String> partial = usage.partialFor(step.id());
            boolean warned = !missing.isEmpty() || !partial.isEmpty();
            warn.setVisible(warned);
            warn.setManaged(warned);
            List<String> tips = new ArrayList<>();
            if (!missing.isEmpty()) {
                tips.add("Not defined before this step: " + String.join(", ", missing));
            }
            if (!partial.isEmpty()) {
                tips.add("Only set on some paths into this step: " + String.join(", ", partial));
            }
            warnTip.setText(String.join("\n", tips));
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
