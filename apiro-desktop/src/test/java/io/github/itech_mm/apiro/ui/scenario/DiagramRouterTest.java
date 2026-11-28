package io.github.itech_mm.apiro.ui.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DiagramRouterTest {

    private static final DiagramRouter.Spec SINGLE = new DiagramRouter.Spec(0, 1, 0, 1, 0, 1, false);

    private static DiagramRouter.Rect card(double x, double y) {
        return new DiagramRouter.Rect(x, y, 230, 100);
    }

    private static DiagramRouter.Route route(DiagramRouter.Rect from, DiagramRouter.Rect to,
                                             List<DiagramRouter.Rect> obstacles) {
        return DiagramRouter.route(from, to, obstacles, SINGLE, new int[DiagramRouter.LANES]);
    }

    @Test
    void forwardEdgeEntersTheLeftSideHorizontally() {
        DiagramRouter.Route route = route(card(0, 0), card(400, 0), List.of());
        DiagramRouter.Segment last = route.segments().get(route.segments().size() - 1);
        assertEquals(1, route.segments().size());
        assertEquals(1, route.dirX(), 1e-9);
        assertEquals(0, route.dirY(), 1e-9);
        assertEquals(400, last.x2(), 1e-9);
        assertEquals(50, last.y2(), 1e-9);
    }

    @Test
    void stackedCardsConnectBottomToTop() {
        DiagramRouter.Route route = route(card(0, 0), card(0, 250), List.of());
        DiagramRouter.Segment last = route.segments().get(route.segments().size() - 1);
        assertEquals(0, route.dirX(), 1e-9);
        assertEquals(1, route.dirY(), 1e-9);
        assertEquals(115, last.x2(), 1e-9);
        assertEquals(250, last.y2(), 1e-9);
    }

    @Test
    void cardAboveIsEnteredFromBelow() {
        DiagramRouter.Route route = route(card(0, 250), card(0, 0), List.of());
        DiagramRouter.Segment last = route.segments().get(route.segments().size() - 1);
        assertEquals(-1, route.dirY(), 1e-9);
        assertEquals(100, last.y2(), 1e-9);
    }

    @Test
    void backwardEdgeArcsOverTheTopAndEntersDownwards() {
        DiagramRouter.Route route = route(card(500, 0), card(0, 0), List.of());
        DiagramRouter.Segment last = route.segments().get(route.segments().size() - 1);
        assertEquals(0, route.dirX(), 1e-9);
        assertEquals(1, route.dirY(), 1e-9);
        assertEquals(0, last.y2(), 1e-9);
        assertTrue(route.minY() < 0);
    }

    @Test
    void blockedEdgeDetoursAroundTheCardInTheWay() {
        DiagramRouter.Rect blocker = card(300, 0);
        DiagramRouter.Route route = route(card(0, 0), card(600, 0), List.of(blocker));
        assertEquals(2, route.segments().size());
        double lane = route.segments().get(0).y2();
        assertTrue(lane < blocker.y() - 10 || lane > blocker.bottom() + 10);
        assertEquals(1, route.dirX(), 1e-9);
        assertEquals(0, route.dirY(), 1e-9);
    }

    @Test
    void parallelDetoursUseSeparateLanes() {
        DiagramRouter.Rect blocker = card(300, 0);
        int[] lanes = new int[DiagramRouter.LANES];
        DiagramRouter.Route first = DiagramRouter.route(card(0, 0), card(600, 0), List.of(blocker), SINGLE, lanes);
        DiagramRouter.Route second = DiagramRouter.route(card(0, 0), card(600, 0), List.of(blocker), SINGLE, lanes);
        assertTrue(first.segments().get(0).y2() != second.segments().get(0).y2());
    }
}
