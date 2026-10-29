package dev.vexelray.fields.shape;

import java.util.ArrayList;
import java.util.List;

/**
 * A filled outline: closed contours, filled by the nonzero rule. Built with {@link #builder()}, from SVG path data
 * with {@link PathData#parse}, or from contours in hand. Its units are its own; a {@link dev.vexelray.fields.Framing}
 * places it on a field's texels.
 */
public record Shape(List<Contour> contours) {

    public Shape {
        contours = List.copyOf(contours);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Bounds bounds() {
        Bounds b = Bounds.EMPTY;
        for (Contour c : contours) {
            b = b.include(c.bounds());
        }
        return b;
    }

    /** Whether {@code p} is filled, by the nonzero rule. */
    public boolean filled(Vec2 p) {
        return Scanline.of(this, p.y()).filled(p.x());
    }

    /**
     * The shape with every contour wound so that the fill is on its edges' right: what a distance's sign and a
     * multi-channel field's channels assume. Found by looking across one edge of each contour: a contour filled on
     * its left is reversed, and one filled on both sides or neither (a contour inside an overlap) is left as it is.
     */
    public Shape oriented() {
        Bounds b = bounds();
        double eps = 1e-6 * Math.max(1e-12, Math.max(b.width(), b.height()));
        List<Contour> out = new ArrayList<>(contours.size());
        for (Contour c : contours) {
            out.add(fillsLeft(c, eps) ? c.reversed() : c);
        }
        return new Shape(out);
    }

    private boolean fillsLeft(Contour c, double eps) {
        for (Edge e : c.edges()) {
            for (double t : new double[]{0.5, 0.3, 0.7}) {
                Vec2 m = e.point(t);
                Vec2 n = e.direction(t).rightNormal();
                if (n.isZero()) {
                    continue;
                }
                boolean right = filled(m.plus(n.times(eps)));
                boolean left = filled(m.minus(n.times(eps)));
                if (right != left) {
                    return left;
                }
            }
        }
        return false;
    }

    /** A path drawn pen-style: each contour is begun by {@link #moveTo} and closed by {@link #close} or the next move. */
    public static final class Builder {

        private final List<Contour> contours = new ArrayList<>();
        private List<Edge> edges = new ArrayList<>();
        private Vec2 start;
        private Vec2 at;

        public Builder moveTo(double x, double y) {
            close();
            start = at = new Vec2(x, y);
            return this;
        }

        public Builder lineTo(double x, double y) {
            Vec2 p = new Vec2(x, y);
            if (!p.equals(pen())) {
                edges.add(new Edge.Line(at, p));
            }
            at = p;
            return this;
        }

        public Builder quadTo(double cx, double cy, double x, double y) {
            edges.add(new Edge.Quadratic(pen(), new Vec2(cx, cy), new Vec2(x, y)));
            at = new Vec2(x, y);
            return this;
        }

        public Builder cubicTo(double c1x, double c1y, double c2x, double c2y, double x, double y) {
            edges.add(new Edge.Cubic(pen(), new Vec2(c1x, c1y), new Vec2(c2x, c2y), new Vec2(x, y)));
            at = new Vec2(x, y);
            return this;
        }

        /** Close the contour being drawn, with a line back to its start if it is not there. */
        public Builder close() {
            if (at != null && !edges.isEmpty()) {
                if (!at.equals(start)) {
                    edges.add(new Edge.Line(at, start));
                }
                contours.add(new Contour(edges));
            }
            edges = new ArrayList<>();
            at = start;
            return this;
        }

        /** Where the pen is: the last point drawn to, or the origin before any move. */
        public Vec2 pen() {
            if (at == null) {
                start = at = Vec2.ZERO;
            }
            return at;
        }

        public Shape build() {
            close();
            return new Shape(contours);
        }
    }
}
