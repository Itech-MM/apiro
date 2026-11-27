package io.github.itech_mm.apiro.ui.scenario;

import io.github.itech_mm.apiro.model.Edge;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.service.scenario.ScenarioGraph;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DiagramLayout {

    record Point(double x, double y) {
    }

    record GraphLayout(Map<String, Point> positions, double width, double height) {
    }

    private DiagramLayout() {
    }

    static int columns(double width, double targetZoom, double margin, double nodeWidth, double gap, int min,
            int max) {
        double usable = (width - 2 * margin) / targetZoom;
        int fit = (int) Math.floor((usable + gap) / (nodeWidth + gap));
        return Math.max(min, Math.min(max, fit));
    }

    static int row(int index, int cols) {
        return index / cols;
    }

    static int direction(int index, int cols) {
        return row(index, cols) % 2 == 0 ? 1 : -1;
    }

    static int column(int index, int cols) {
        int offset = index % cols;
        return direction(index, cols) > 0 ? offset : cols - 1 - offset;
    }

    static double nodeX(int index, int cols, double nodeWidth, double gap) {
        return column(index, cols) * (nodeWidth + gap);
    }

    static double nodeY(int index, int cols, double nodeHeight, double rowGap) {
        return row(index, cols) * (nodeHeight + rowGap);
    }

    static boolean turns(int from, int cols) {
        return (from + 1) % cols == 0;
    }

    static double contentWidth(int count, int cols, double nodeWidth, double gap) {
        int used = Math.max(1, Math.min(count, cols));
        return used * nodeWidth + (used - 1) * gap;
    }

    static double contentHeight(int count, int cols, double nodeHeight, double rowGap) {
        int rows = Math.max(1, (count + cols - 1) / cols);
        return rows * nodeHeight + (rows - 1) * rowGap;
    }

    static Point gapAnchor(int gap, int count, int cols, double nodeWidth, double nodeHeight, double gapWidth,
            double rowGap) {
        if (count == 0) {
            return new Point(-gapWidth / 2, nodeHeight / 2);
        }
        boolean after = gap >= count;
        int node = after ? count - 1 : gap;
        int direction = direction(node, cols);
        double x = nodeX(node, cols, nodeWidth, gapWidth);
        double y = nodeY(node, cols, nodeHeight, rowGap) + nodeHeight / 2;
        boolean rightSide = after ? direction > 0 : direction < 0;
        return new Point(rightSide ? x + nodeWidth + gapWidth / 2 : x - gapWidth / 2, y);
    }

    static int nearestGap(double worldX, double worldY, int count, int cols, double nodeWidth, double nodeHeight,
            double gapWidth, double rowGap) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int gap = 0; gap <= count; gap++) {
            Point anchor = gapAnchor(gap, count, cols, nodeWidth, nodeHeight, gapWidth, rowGap);
            double dx = anchor.x() - worldX;
            double dy = anchor.y() - worldY;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = gap;
            }
        }
        return best;
    }

    static int targetAfterRemoval(int from, int gap) {
        return gap > from ? gap - 1 : gap;
    }

    static GraphLayout graphLayout(ScenarioItem scenario, double nodeWidth, double nodeHeight, double gapX,
                                   double gapY) {
        if (scenario == null || scenario.steps().isEmpty()) {
            return new GraphLayout(Map.of(), nodeWidth, nodeHeight);
        }
        List<Step> steps = scenario.steps();
        Map<String, Integer> stepOrder = new HashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            stepOrder.put(steps.get(i).id(), i);
        }
        ScenarioGraph.Plan plan = ScenarioGraph.plan(scenario);
        Map<String, Integer> rank = plan.valid()
                ? validRanks(plan)
                : fallbackRanks(steps, scenario.edges());

        int maxCol = 0;
        Map<Integer, List<String>> columns = new LinkedHashMap<>();
        for (Step step : steps) {
            int col = rank.getOrDefault(step.id(), 0);
            maxCol = Math.max(maxCol, col);
            columns.computeIfAbsent(col, k -> new ArrayList<>()).add(step.id());
        }

        Map<String, Double> rowSlot = new HashMap<>();
        Map<String, List<Edge>> outgoing = outgoingMap(steps, scenario.edges());
        Map<String, List<Edge>> incoming = incomingMap(steps, scenario.edges());
        for (int col = 0; col <= maxCol; col++) {
            List<String> ids = columns.getOrDefault(col, List.of());
            if (col > 0 && ids.size() > 1) {
                ids.sort(Comparator
                        .comparingDouble((String id) -> barycenter(id, incoming, rowSlot))
                        .thenComparingInt(id -> edgePriority(id, incoming, outgoing))
                        .thenComparingInt(id -> stepOrder.getOrDefault(id, 0)));
            }
            for (int r = 0; r < ids.size(); r++) {
                rowSlot.put(ids.get(r), (double) r);
            }
        }

        int maxRows = 1;
        for (List<String> ids : columns.values()) {
            maxRows = Math.max(maxRows, ids.size());
        }
        double totalHeight = maxRows * nodeHeight + (maxRows - 1) * gapY;
        double totalWidth = (maxCol + 1) * nodeWidth + maxCol * gapX;
        Map<String, Point> positions = new LinkedHashMap<>();
        for (int col = 0; col <= maxCol; col++) {
            List<String> ids = columns.getOrDefault(col, List.of());
            if (ids.isEmpty()) {
                continue;
            }
            double colHeight = ids.size() * nodeHeight + (ids.size() - 1) * gapY;
            double startY = (totalHeight - colHeight) / 2.0;
            double x = col * (nodeWidth + gapX);
            for (int r = 0; r < ids.size(); r++) {
                double y = startY + r * (nodeHeight + gapY);
                positions.put(ids.get(r), new Point(x, y));
            }
        }
        return new GraphLayout(Map.copyOf(positions), Math.max(nodeWidth, totalWidth),
                Math.max(nodeHeight, totalHeight));
    }

    private static Map<String, Integer> validRanks(ScenarioGraph.Plan plan) {
        Map<String, Integer> rank = new HashMap<>();
        for (Step step : plan.steps()) {
            int r = rank.getOrDefault(step.id(), 0);
            rank.putIfAbsent(step.id(), r);
            for (Edge edge : plan.outgoingOf(step.id())) {
                rank.put(edge.to(), Math.max(rank.getOrDefault(edge.to(), 0), r + 1));
            }
        }
        return rank;
    }

    private static Map<String, Integer> fallbackRanks(List<Step> steps, List<Edge> edges) {
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (Step step : steps) {
            inDegree.put(step.id(), 0);
        }
        for (Edge edge : edges) {
            if (inDegree.containsKey(edge.from()) && inDegree.containsKey(edge.to())
                    && !edge.from().equals(edge.to())) {
                adj.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge.to());
                inDegree.merge(edge.to(), 1, Integer::sum);
            }
        }
        Map<String, Integer> rank = new HashMap<>();
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (Step step : steps) {
            if (inDegree.get(step.id()) == 0) {
                visited.add(step.id());
                rank.put(step.id(), 0);
                queue.add(step.id());
            }
        }
        while (!queue.isEmpty()) {
            String u = queue.poll();
            int nextRank = rank.get(u) + 1;
            for (String v : adj.getOrDefault(u, List.of())) {
                if (visited.add(v)) {
                    rank.put(v, nextRank);
                    queue.add(v);
                }
            }
        }
        int orphanRank = rank.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        for (Step step : steps) {
            if (!visited.contains(step.id())) {
                visited.add(step.id());
                rank.put(step.id(), orphanRank);
                queue.add(step.id());
                while (!queue.isEmpty()) {
                    String u = queue.poll();
                    int nextRank = rank.get(u) + 1;
                    for (String v : adj.getOrDefault(u, List.of())) {
                        if (visited.add(v)) {
                            rank.put(v, nextRank);
                            queue.add(v);
                        }
                    }
                }
                orphanRank = rank.values().stream().mapToInt(Integer::intValue).max().orElse(orphanRank) + 1;
            }
        }
        return rank;
    }

    private static Map<String, List<Edge>> outgoingMap(List<Step> steps, List<Edge> edges) {
        Map<String, List<Edge>> map = new HashMap<>();
        if (edges.isEmpty()) {
            for (int i = 1; i < steps.size(); i++) {
                Edge e = new Edge(steps.get(i - 1).id(), steps.get(i).id());
                map.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e);
            }
            return map;
        }
        for (Edge edge : edges) {
            map.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
        }
        return map;
    }

    private static Map<String, List<Edge>> incomingMap(List<Step> steps, List<Edge> edges) {
        Map<String, List<Edge>> map = new HashMap<>();
        if (edges.isEmpty()) {
            for (int i = 1; i < steps.size(); i++) {
                Edge e = new Edge(steps.get(i - 1).id(), steps.get(i).id());
                map.computeIfAbsent(e.to(), k -> new ArrayList<>()).add(e);
            }
            return map;
        }
        for (Edge edge : edges) {
            map.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge);
        }
        return map;
    }

    private static double barycenter(String stepId, Map<String, List<Edge>> incoming, Map<String, Double> rowSlot) {
        List<Edge> parents = incoming.getOrDefault(stepId, List.of());
        double sum = 0;
        int count = 0;
        for (Edge edge : parents) {
            Double slot = rowSlot.get(edge.from());
            if (slot != null) {
                sum += slot;
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    private static int edgePriority(String stepId, Map<String, List<Edge>> incoming, Map<String, List<Edge>> outgoing) {
        int best = Integer.MAX_VALUE;
        for (Edge in : incoming.getOrDefault(stepId, List.of())) {
            List<Edge> siblings = outgoing.getOrDefault(in.from(), List.of());
            for (int i = 0; i < siblings.size(); i++) {
                if (siblings.get(i).to().equals(stepId)) {
                    best = Math.min(best, i);
                }
            }
        }
        return best;
    }
}
