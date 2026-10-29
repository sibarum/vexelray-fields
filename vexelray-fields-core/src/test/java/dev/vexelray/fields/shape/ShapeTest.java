package dev.vexelray.fields.shape;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeTest {

    @Test
    void aLineIsPositiveOnItsRight() {
        Edge e = new Edge.Line(new Vec2(0, 0), new Vec2(10, 0));
        assertEquals(2, e.nearest(new Vec2(5, -2)).distance().distance(), 1e-12);
        assertEquals(-2, e.nearest(new Vec2(5, 2)).distance().distance(), 1e-12);
        // past the end: distance to the end, pseudo-distance to the line running on
        Edge.Nearest past = e.nearest(new Vec2(13, -4));
        assertEquals(5, past.distance().distance(), 1e-12);
        assertEquals(4, e.pseudoDistance(past, new Vec2(13, -4)).distance(), 1e-12);
    }

    @Test
    void curvesFindTheirNearestPointAsSamplingDoes() {
        Edge[] edges = {
                new Edge.Quadratic(new Vec2(0, 0), new Vec2(5, 10), new Vec2(10, 0)),
                new Edge.Cubic(new Vec2(0, 0), new Vec2(0, 10), new Vec2(10, -10), new Vec2(10, 0)),
                new Edge.Cubic(new Vec2(0, 0), new Vec2(12, 8), new Vec2(-2, 8), new Vec2(10, 0)),
        };
        Random r = new Random(7);
        for (Edge e : edges) {
            for (int k = 0; k < 400; k++) {
                Vec2 p = new Vec2(r.nextDouble() * 20 - 5, r.nextDouble() * 20 - 10);
                double brute = Double.MAX_VALUE;
                for (int i = 0; i <= 20000; i++) {
                    brute = Math.min(brute, e.point(i / 20000.0).minus(p).length());
                }
                double found = Math.abs(e.nearest(p).distance().distance());
                assertEquals(brute, found, 1e-3, () -> e + " at " + p);
            }
        }
    }

    @Test
    void cubicRootsAreRoots() {
        double[] x = new double[3];
        int n = Equations.cubic(x, 1, -6, 11, -6);   // (x-1)(x-2)(x-3)
        assertEquals(3, n);
        java.util.Arrays.sort(x);
        assertEquals(1, x[0], 1e-9);
        assertEquals(2, x[1], 1e-9);
        assertEquals(3, x[2], 1e-9);
        assertEquals(1, Equations.cubic(x, 0, 0, 2, -4));
        assertEquals(2, x[0], 1e-12);
    }

    @Test
    void theNonzeroRuleFillsHolesWoundTheOtherWayOnly() {
        Shape opposite = PathData.parse("M0 0 H10 V10 H0 Z M3 3 V7 H7 V3 Z");
        assertTrue(opposite.filled(new Vec2(1, 1)));
        assertFalse(opposite.filled(new Vec2(5, 5)));
        assertFalse(opposite.filled(new Vec2(11, 5)));
        Shape same = PathData.parse("M0 0 H10 V10 H0 Z M3 3 H7 V7 H3 Z");
        assertTrue(same.filled(new Vec2(5, 5)));
    }

    @Test
    void aScanlineThroughAVertexCountsItOnce() {
        Shape diamond = PathData.parse("M5 0 L10 5 L5 10 L0 5 Z");
        Scanline line = Scanline.of(diamond, 5);   // through the left and right vertices
        assertEquals(2, line.size());
        assertTrue(line.filled(5));
        assertFalse(Scanline.of(diamond, 0).filled(5));   // the top vertex is a turn: no crossing at all
    }

    @Test
    void orientingPutsTheFillOnEveryEdgesRight() {
        for (String d : new String[]{"M0 0 H10 V10 H0 Z M3 3 V7 H7 V3 Z", "M0 0 V10 H10 V0 Z M3 3 H7 V7 H3 Z"}) {
            Shape s = PathData.parse(d).oriented();
            for (Contour c : s.contours()) {
                for (Edge e : c.edges()) {
                    Vec2 m = e.point(0.5);
                    assertTrue(s.filled(m.plus(e.direction(0.5).rightNormal().times(0.01))), d);
                    assertFalse(s.filled(m.minus(e.direction(0.5).rightNormal().times(0.01))), d);
                }
            }
        }
    }

    @Test
    void pathDataReadsCurvesRelativeAndSmooth() {
        Shape s = PathData.parse("m10 10 q5 -5 10 0 t10 0 c0 5 -5 10 -10 10 s-10 -5 -10 -10 z");
        Contour c = s.contours().getFirst();
        assertEquals(4, c.edges().size());   // q, t, c and s, which ends where the contour began: no closing line
        Edge.Quadratic t = (Edge.Quadratic) c.edges().get(1);
        assertEquals(new Vec2(25, 15), t.p1());   // the first control point reflected
        assertEquals(new Vec2(30, 10), t.p2());
        assertThrows(IllegalArgumentException.class, () -> PathData.parse("M0 0 A5 5 0 0 1 10 0"));
    }

    @Test
    void thirdsMeetAndFollowTheCurve() {
        Edge q = new Edge.Quadratic(new Vec2(0, 0), new Vec2(5, 10), new Vec2(10, 0));
        Edge c = new Edge.Cubic(new Vec2(0, 0), new Vec2(0, 10), new Vec2(10, -10), new Vec2(10, 0));
        for (Edge e : new Edge[]{q, c}) {
            Edge[] p = e.thirds();
            for (int i = 0; i < 3; i++) {
                for (double t = 0; t <= 1; t += 0.25) {
                    Vec2 a = p[i].point(t);
                    Vec2 b = e.point((i + t) / 3);
                    assertEquals(0, a.minus(b).length(), 1e-9);
                }
            }
        }
    }
}
