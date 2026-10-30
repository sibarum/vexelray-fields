package dev.vexelray.fields.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A path as it was drawn: subpaths that are closed or left open, which a {@link Shape} does not keep, being only a fill.
 * What a stroke is drawn along, where an open end takes a cap and a closed one a join. {@link #shape()} is its fill,
 * every subpath closed, as SVG fills an open one.
 */
public record Path(List<Subpath> subpaths) {

    public Path {
        subpaths = List.copyOf(subpaths);
    }

    /**
     * One run of the pen from a move: its edges, and whether it was closed. {@code start} is kept for a subpath of no
     * edges, a move alone, which a round or square cap still draws as a dot.
     */
    public record Subpath(Vec2 start, List<Edge> edges, boolean closed) {

        public Subpath {
            edges = List.copyOf(edges);
        }

        public Subpath mapped(UnaryOperator<Vec2> f) {
            return new Subpath(f.apply(start), edges.stream().map(e -> e.mapped(f)).toList(), closed);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The fill: every subpath with edges, closed by a line back to its start where it is not there. */
    public Shape shape() {
        List<Contour> contours = new ArrayList<>();
        for (Subpath s : subpaths) {
            if (s.edges.isEmpty()) {
                continue;
            }
            List<Edge> edges = new ArrayList<>(s.edges);
            Vec2 end = edges.getLast().end();
            if (!end.equals(s.start)) {
                edges.add(new Edge.Line(end, s.start));
            }
            contours.add(new Contour(edges));
        }
        return new Shape(contours);
    }

    /** The path with every point put through {@code f}; see {@link Edge#mapped}. */
    public Path mapped(UnaryOperator<Vec2> f) {
        return new Path(subpaths.stream().map(s -> s.mapped(f)).toList());
    }

    public Bounds bounds() {
        Bounds b = Bounds.EMPTY;
        for (Subpath s : subpaths) {
            b = b.include(s.start);
            for (Edge e : s.edges) {
                b = b.include(e.bounds());
            }
        }
        return b;
    }

    /** A path drawn pen-style, as {@link Shape.Builder} draws one, keeping which subpaths were closed. */
    public static final class Builder {

        private final List<Subpath> subpaths = new ArrayList<>();
        private List<Edge> edges = new ArrayList<>();
        private Vec2 start;
        private Vec2 at;
        private boolean moved;

        public Builder moveTo(double x, double y) {
            end(false);
            start = at = new Vec2(x, y);
            moved = true;
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

        /**
         * An elliptical arc to {@code (x, y)}, as SVG's {@code A} command has it: radii {@code rx} and {@code ry}, the
         * ellipse turned {@code rotation} degrees, the larger or smaller of the two arcs, and which way round. Drawn
         * as cubics of a quarter turn or less each, within about 0.03% of the radius. Radii too small to reach are
         * scaled up until they do; a radius of zero is a line, as the specification says.
         */
        public Builder arcTo(double rx, double ry, double rotation, boolean large, boolean sweep, double x, double y) {
            Vec2 p0 = pen();
            if (p0.x() == x && p0.y() == y) {
                return this;
            }
            rx = Math.abs(rx);
            ry = Math.abs(ry);
            if (rx == 0 || ry == 0) {
                return lineTo(x, y);
            }
            // endpoint to centre parameterisation, SVG 1.1 appendix F.6.5
            double phi = Math.toRadians(rotation);
            double cos = Math.cos(phi), sin = Math.sin(phi);
            double dx = (p0.x() - x) / 2, dy = (p0.y() - y) / 2;
            double x1 = cos * dx + sin * dy, y1 = -sin * dx + cos * dy;
            double lambda = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry);
            if (lambda > 1) {
                double s = Math.sqrt(lambda);
                rx *= s;
                ry *= s;
            }
            double num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1;
            double den = rx * rx * y1 * y1 + ry * ry * x1 * x1;
            double k = Math.sqrt(Math.max(0, num / den)) * (large == sweep ? -1 : 1);
            double cx1 = k * rx * y1 / ry, cy1 = -k * ry * x1 / rx;
            double cx = cos * cx1 - sin * cy1 + (p0.x() + x) / 2;
            double cy = sin * cx1 + cos * cy1 + (p0.y() + y) / 2;
            double theta = Math.atan2((y1 - cy1) / ry, (x1 - cx1) / rx);
            double delta = Math.atan2((-y1 - cy1) / ry, (-x1 - cx1) / rx) - theta;
            if (sweep && delta < 0) {
                delta += 2 * Math.PI;
            } else if (!sweep && delta > 0) {
                delta -= 2 * Math.PI;
            }
            int n = (int) Math.ceil(Math.abs(delta) / (Math.PI / 2) - 1e-9);
            double step = delta / n;
            double t = 4.0 / 3 * Math.tan(step / 4);
            for (int i = 0; i < n; i++) {
                double a = theta + i * step, b = a + step;
                double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
                // on the unit circle: from (ca, sa) to (cb, sb), handles along the tangents
                double[] c1 = onEllipse(ca - t * sa, sa + t * ca, rx, ry, cos, sin, cx, cy);
                double[] c2 = onEllipse(cb + t * sb, sb - t * cb, rx, ry, cos, sin, cx, cy);
                double[] e = i == n - 1 ? new double[]{x, y} : onEllipse(cb, sb, rx, ry, cos, sin, cx, cy);
                cubicTo(c1[0], c1[1], c2[0], c2[1], e[0], e[1]);
            }
            return this;
        }

        private static double[] onEllipse(double u, double v, double rx, double ry, double cos, double sin,
                                          double cx, double cy) {
            double px = u * rx, py = v * ry;
            return new double[]{cos * px - sin * py + cx, sin * px + cos * py + cy};
        }

        /**
         * Close the subpath being drawn, its ends joined. Drawing on without a move starts a new subpath where this one
         * began, as SVG has it.
         */
        public Builder close() {
            end(true);
            at = start;
            return this;
        }

        /** A subpath is kept if it has edges, or was begun by a move: a move alone is a dot a cap can draw. */
        private void end(boolean closed) {
            if (moved || !edges.isEmpty()) {
                subpaths.add(new Subpath(start, edges, closed));
            }
            edges = new ArrayList<>();
            moved = false;
        }

        /** Where the pen is: the last point drawn to, or the origin before any move. */
        public Vec2 pen() {
            if (at == null) {
                start = at = Vec2.ZERO;
            }
            return at;
        }

        public Path build() {
            end(false);
            return new Path(subpaths);
        }
    }
}
