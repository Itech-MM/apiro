package io.github.itech_mm.apiro.ui.scenario;

import java.util.ArrayList;
import java.util.List;

final class DiagramRouter {

    static final int LANES = 7;

    private static final double MIN_GAP = 24;
    private static final double MARGIN = 10;
    private static final double LANE_BASE = 46;
    private static final double LANE_STEP = 34;
    private static final int SAMPLES = 32;

    record Rect(double x, double y, double w, double h) {

        double right() {
            return x + w;
        }

        double bottom() {
            return y + h;
        }

        double cx() {
            return x + w / 2.0;
        }

        double cy() {
            return y + h / 2.0;
        }
    }

    record Segment(double x1, double y1, double cx1, double cy1, double cx2, double cy2, double x2, double y2) {

        double xAt(double t) {
            double u = 1 - t;
            return u * u * u * x1 + 3 * u * u * t * cx1 + 3 * u * t * t * cx2 + t * t * t * x2;
        }

        double yAt(double t) {
            double u = 1 - t;
            return u * u * u * y1 + 3 * u * u * t * cy1 + 3 * u * t * t * cy2 + t * t * t * y2;
        }
    }

    record Route(List<Segment> segments, double dirX, double dirY, double labelX, double labelY,
                 double minX, double minY, double maxX, double maxY) {

        static Route of(List<Segment> segments, double dirX, double dirY) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (Segment s : segments) {
                double[] xs = {s.x1(), s.cx1(), s.cx2(), s.x2()};
                double[] ys = {s.y1(), s.cy1(), s.cy2(), s.y2()};
                for (double x : xs) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                }
                for (double y : ys) {
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
            double labelX;
            double labelY;
            if (segments.size() == 1) {
                labelX = segments.get(0).xAt(0.5);
                labelY = segments.get(0).yAt(0.5);
            } else {
                labelX = segments.get(0).x2();
                labelY = segments.get(0).y2();
            }
            return new Route(List.copyOf(segments), dirX, dirY, labelX, labelY, minX, minY, maxX, maxY);
        }

        static Route single(double x1, double y1, double cx1, double cy1, double cx2, double cy2, double x2,
                            double y2, double dirX, double dirY) {
            return of(List.of(new Segment(x1, y1, cx1, cy1, cx2, cy2, x2, y2)), dirX, dirY);
        }
    }

    record Spec(int outIndex, int outCount, int inIndex, int inCount, int pairIndex, int pairTotal,
                boolean preferAfter) {
    }

    private DiagramRouter() {
    }

    static Route route(Rect from, Rect to, List<Rect> obstacles, Spec spec, int[] lanes) {
        double gapRight = to.x() - from.right();
        double gapLeft = from.x() - to.right();
        double spread = (spec.pairIndex() - (spec.pairTotal() - 1) / 2.0) * 34.0;
        double outOff = (spec.outIndex() - (spec.outCount() - 1) / 2.0) * 14.0 + spread;
        double inOff = (spec.inIndex() - (spec.inCount() - 1) / 2.0) * 14.0 + spread;
        if (gapRight >= MIN_GAP) {
            return flow(0, from, to, obstacles, outOff, inOff, spec, lanes);
        }
        if (gapLeft >= MIN_GAP) {
            return backArc(from, to, obstacles, outOff, inOff, lanes);
        }
        return flow(to.cy() >= from.cy() ? 1 : 2, from, to, obstacles, outOff, inOff, spec, lanes);
    }

    private static Route flow(int frame, Rect from, Rect to, List<Rect> obstacles, double outOff, double inOff,
                              Spec spec, int[] lanes) {
        Rect f = toLocal(from, frame);
        Rect t = toLocal(to, frame);
        List<Rect> o = new ArrayList<>(obstacles.size());
        for (Rect obstacle : obstacles) {
            o.add(toLocal(obstacle, frame));
        }
        double limit = Math.max(8, Math.min(f.h(), t.h()) / 2.0 - 12);
        double y1 = f.cy() + clamp(outOff, -limit, limit);
        double y2 = t.cy() + clamp(inOff, -limit, limit);
        double x1 = f.right();
        double x2 = t.x();
        double h = clamp(Math.max(x2 - x1, 12) * 0.42, 36, 120);
        Segment direct = new Segment(x1, y1, x1 + h, y1, x2 - h, y2, x2, y2);
        List<Segment> best = List.of(direct);
        int bestHits = hits(best, o);
        int bestLane = -1;
        if (bestHits > 0) {
            double top = Double.POSITIVE_INFINITY;
            double bottom = Double.NEGATIVE_INFINITY;
            for (Rect r : o) {
                if (r.right() > x1 && r.x() < x2) {
                    top = Math.min(top, r.y());
                    bottom = Math.max(bottom, r.bottom());
                }
            }
            if (top != Double.POSITIVE_INFINITY) {
                double mid = (y1 + y2) / 2.0;
                double above = top - LANE_BASE - lanes[frame * 2] * LANE_STEP;
                double below = bottom + LANE_BASE + lanes[frame * 2 + 1] * LANE_STEP;
                double bias = spec.preferAfter() ? 12 : -12;
                double bestCost = Double.POSITIVE_INFINITY;
                for (int side = 0; side < 2; side++) {
                    double lane = side == 0 ? above : below;
                    List<Segment> candidate = detour(x1, y1, x2, y2, lane);
                    int count = hits(candidate, o);
                    double cost = Math.abs(lane - mid) + (side == 1 ? -bias : bias);
                    if (count < bestHits || count == bestHits && bestLane >= 0 && cost < bestCost) {
                        best = candidate;
                        bestHits = count;
                        bestCost = cost;
                        bestLane = frame * 2 + side;
                    }
                }
            }
        }
        if (bestLane >= 0) {
            lanes[bestLane]++;
        }
        List<Segment> world = new ArrayList<>(best.size());
        for (Segment s : best) {
            world.add(toWorld(s, frame));
        }
        double dx = frame == 0 ? 1 : 0;
        double dy = frame == 1 ? 1 : frame == 2 ? -1 : 0;
        return Route.of(world, dx, dy);
    }

    private static List<Segment> detour(double x1, double y1, double x2, double y2, double lane) {
        double xm = (x1 + x2) / 2.0;
        double h = clamp((xm - x1) * 0.55, 30, 110);
        return List.of(
                new Segment(x1, y1, x1 + h, y1, xm - h, lane, xm, lane),
                new Segment(xm, lane, xm + h, lane, x2 - h, y2, x2, y2));
    }

    private static Route backArc(Rect from, Rect to, List<Rect> obstacles, double outOff, double inOff,
                                 int[] lanes) {
        double x1 = from.cx() + clamp(outOff * 0.5, -60, 60);
        double y1 = from.y();
        double x2 = to.cx() + clamp(inOff * 0.5, -60, 60);
        double y2 = to.y();
        double minX = Math.min(from.x(), to.x()) - 20;
        double maxX = Math.max(from.right(), to.right()) + 20;
        double minTop = Math.min(from.y(), to.y());
        for (Rect r : obstacles) {
            if (r.right() > minX && r.x() < maxX) {
                minTop = Math.min(minTop, r.y());
            }
        }
        double apex = minTop - (56 + lanes[6] * LANE_STEP);
        lanes[6]++;
        double cy = (apex - 0.25 * ((y1 + y2) / 2.0)) / 0.75;
        return Route.single(x1, y1, x1, cy, x2, cy, x2, y2, 0, 1);
    }

    private static int hits(List<Segment> segments, List<Rect> obstacles) {
        int count = 0;
        for (Segment s : segments) {
            for (int i = 0; i <= SAMPLES; i++) {
                double t = i / (double) SAMPLES;
                double x = s.xAt(t);
                double y = s.yAt(t);
                for (Rect r : obstacles) {
                    if (x > r.x() - MARGIN && x < r.right() + MARGIN && y > r.y() - MARGIN
                            && y < r.bottom() + MARGIN) {
                        count++;
                        break;
                    }
                }
            }
        }
        return count;
    }

    private static Rect toLocal(Rect r, int frame) {
        return switch (frame) {
            case 1 -> new Rect(r.y(), r.x(), r.h(), r.w());
            case 2 -> new Rect(-r.bottom(), r.x(), r.h(), r.w());
            default -> r;
        };
    }

    private static Segment toWorld(Segment s, int frame) {
        return switch (frame) {
            case 1 -> new Segment(s.y1(), s.x1(), s.cy1(), s.cx1(), s.cy2(), s.cx2(), s.y2(), s.x2());
            case 2 -> new Segment(s.y1(), -s.x1(), s.cy1(), -s.cx1(), s.cy2(), -s.cx2(), s.y2(), -s.x2());
            default -> s;
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
