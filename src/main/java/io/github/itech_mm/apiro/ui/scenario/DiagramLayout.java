package io.github.itech_mm.apiro.ui.scenario;

final class DiagramLayout {

    record Point(double x, double y) {
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
}
