package io.github.itech_mm.apiro.ui.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.BranchRule;
import io.github.itech_mm.apiro.model.Edge;
import io.github.itech_mm.apiro.model.EdgeCondition;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiagramLayoutTest {

    private static final double W = 230;
    private static final double H = 100;
    private static final double G = 80;
    private static final double V = 70;

    @Test
    void columnsFollowTheViewportAndStayInRange() {
        assertEquals(2, DiagramLayout.columns(200, 0.8, 40, W, G, 2, 8));
        assertEquals(3, DiagramLayout.columns(900, 0.8, 40, W, G, 2, 8));
        assertEquals(8, DiagramLayout.columns(9000, 0.8, 40, W, G, 2, 8));
    }

    @Test
    void rowsSnakeBackAndForth() {
        assertEquals(0, DiagramLayout.column(0, 3));
        assertEquals(2, DiagramLayout.column(2, 3));
        assertEquals(2, DiagramLayout.column(3, 3));
        assertEquals(0, DiagramLayout.column(5, 3));
        assertEquals(0, DiagramLayout.column(6, 3));
        assertEquals(-1, DiagramLayout.direction(3, 3));
        assertEquals(1, DiagramLayout.direction(6, 3));
    }

    @Test
    void edgeTurnsOnlyAtTheEndOfARow() {
        assertTrue(DiagramLayout.turns(2, 3));
        assertTrue(DiagramLayout.turns(5, 3));
        assertFalse(DiagramLayout.turns(0, 3));
        assertFalse(DiagramLayout.turns(3, 3));
    }

    @Test
    void positionsFollowTheGrid() {
        assertEquals(0, DiagramLayout.nodeX(0, 3, W, G));
        assertEquals(2 * (W + G), DiagramLayout.nodeX(3, 3, W, G));
        assertEquals(H + V, DiagramLayout.nodeY(3, 3, H, V));
    }

    @Test
    void contentSizeCoversAllRows() {
        assertEquals(3 * W + 2 * G, DiagramLayout.contentWidth(7, 3, W, G));
        assertEquals(2 * W + G, DiagramLayout.contentWidth(2, 3, W, G));
        assertEquals(3 * H + 2 * V, DiagramLayout.contentHeight(7, 3, H, V));
        assertEquals(H, DiagramLayout.contentHeight(0, 3, H, V));
    }

    @Test
    void pointNearAnAnchorPicksThatGap() {
        for (int gap = 0; gap <= 5; gap++) {
            DiagramLayout.Point anchor = DiagramLayout.gapAnchor(gap, 5, 3, W, H, G, V);
            assertEquals(gap, DiagramLayout.nearestGap(anchor.x(), anchor.y(), 5, 3, W, H, G, V));
        }
    }

    @Test
    void emptyChainAlwaysDropsAtZero() {
        assertEquals(0, DiagramLayout.nearestGap(500, 500, 0, 3, W, H, G, V));
    }

    @Test
    void movingForwardShiftsTheTargetByOne() {
        assertEquals(2, DiagramLayout.targetAfterRemoval(0, 3));
        assertEquals(0, DiagramLayout.targetAfterRemoval(2, 0));
        assertEquals(1, DiagramLayout.targetAfterRemoval(1, 1));
    }

    @Test
    void graphLayoutPlacesBranchesInPriorityOrderAndCentersMerges() {
        Step a = new Step("a", "r1", "A", null, null, 0, null, 0, 0);
        Step b = new Step("b", "r2", "B", null, null, 0, null, 0, 0);
        Step c = new Step("c", "r3", "C", null, null, 0, null, 0, 0);
        Step d = new Step("d", "r4", "D", null, null, 0, null, 0, 0);
        ScenarioItem diamond = new ScenarioItem("sc", "Diamond", null, List.of(a, c, b, d),
                List.of(
                        Edge.of("a", "b", EdgeCondition.all(BranchRule.status(BranchRule.Operator.EQUALS, "200")), "ok"),
                        Edge.of("a", "c", EdgeCondition.ELSE, "else"),
                        Edge.of("b", "d"),
                        Edge.of("c", "d")),
                null);

        DiagramLayout.GraphLayout layout = DiagramLayout.graphLayout(diamond, W, H, G, V);

        DiagramLayout.Point pa = layout.positions().get("a");
        DiagramLayout.Point pb = layout.positions().get("b");
        DiagramLayout.Point pc = layout.positions().get("c");
        DiagramLayout.Point pd = layout.positions().get("d");

        assertEquals(0, pa.x());
        assertEquals(W + G, pb.x());
        assertEquals(W + G, pc.x());
        assertEquals(2 * (W + G), pd.x());
        assertTrue(pb.y() < pc.y());
        assertEquals(pa.y(), pd.y());
    }

    @Test
    void graphLayoutHandlesDisconnectedAndCyclicGraphsSafely() {
        Step a = new Step("a", "r1", "A", null, null, 0, null, 0, 0);
        Step b = new Step("b", "r2", "B", null, null, 0, null, 0, 0);
        Step c = new Step("c", "r3", "C", null, null, 0, null, 0, 0);
        ScenarioItem cyclic = new ScenarioItem("sc", "Cyclic", null, List.of(a, b, c),
                List.of(Edge.of("a", "b"), Edge.of("b", "a")), null);

        DiagramLayout.GraphLayout layout = DiagramLayout.graphLayout(cyclic, W, H, G, V);

        assertEquals(3, layout.positions().size());
        assertFalse(layout.positions().get("a").equals(layout.positions().get("b")));
        assertFalse(layout.positions().get("a").equals(layout.positions().get("c")));
    }
}
