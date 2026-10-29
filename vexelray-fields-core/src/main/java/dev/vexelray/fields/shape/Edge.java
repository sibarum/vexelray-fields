package dev.vexelray.fields.shape;

/**
 * One piece of a contour: a straight line, or a quadratic or cubic Bézier curve, run from {@code t = 0} to
 * {@code t = 1}. Its signed distances are msdfgen's, so a field made here agrees with one made there.
 */
public sealed interface Edge permits Edge.Line, Edge.Quadratic, Edge.Cubic {

    /** The point nearest a query, and where along the edge it falls; {@code t} outside 0..1 is past an end. */
    record Nearest(SignedDistance distance, double t) {
    }

    Vec2 point(double t);

    /** The tangent at {@code t}, not normalised; never zero for a curve with a degenerate control point. */
    Vec2 direction(double t);

    default Vec2 start() {
        return point(0);
    }

    default Vec2 end() {
        return point(1);
    }

    Nearest nearest(Vec2 p);

    /** The box round the edge, its extremes included. */
    Bounds bounds();

    /** Where, strictly between the ends, the edge turns in y: the ends of its y-monotone pieces. */
    double[] yTurns();

    Edge reversed();

    /** The edge in three, at a third and two thirds: what a contour of one edge is coloured by. */
    Edge[] thirds();

    /**
     * The distance a multi-channel field keeps: past an end, the distance to the end's tangent line rather than to
     * the end itself, when that is nearer, so two edges of a corner meet in a sharp line instead of a round one.
     */
    default SignedDistance pseudoDistance(Nearest n, Vec2 p) {
        SignedDistance d = n.distance();
        if (n.t() < 0) {
            Vec2 dir = direction(0).normalize();
            Vec2 aq = p.minus(point(0));
            if (aq.dot(dir) < 0) {
                double pseudo = aq.cross(dir);
                if (Math.abs(pseudo) <= Math.abs(d.distance())) {
                    return new SignedDistance(pseudo, 0);
                }
            }
        } else if (n.t() > 1) {
            Vec2 dir = direction(1).normalize();
            Vec2 bq = p.minus(point(1));
            if (bq.dot(dir) > 0) {
                double pseudo = bq.cross(dir);
                if (Math.abs(pseudo) <= Math.abs(d.distance())) {
                    return new SignedDistance(pseudo, 0);
                }
            }
        }
        return d;
    }

    private static double sign(double v) {
        return v > 0 ? 1 : -1;
    }

    /** A straight line from {@code p0} to {@code p1}. */
    record Line(Vec2 p0, Vec2 p1) implements Edge {

        @Override
        public Vec2 point(double t) {
            return Vec2.mix(p0, p1, t);
        }

        @Override
        public Vec2 direction(double t) {
            return p1.minus(p0);
        }

        @Override
        public Nearest nearest(Vec2 p) {
            Vec2 aq = p.minus(p0);
            Vec2 ab = p1.minus(p0);
            double t = aq.dot(ab) / ab.dot(ab);
            Vec2 eq = (t > 0.5 ? p1 : p0).minus(p);
            double endDistance = eq.length();
            if (t > 0 && t < 1) {
                double ortho = ab.rightNormal().dot(aq);
                if (Math.abs(ortho) < endDistance) {
                    return new Nearest(new SignedDistance(ortho, 0), t);
                }
            }
            return new Nearest(new SignedDistance(sign(aq.cross(ab)) * endDistance,
                    Math.abs(ab.normalize().dot(eq.normalize()))), t);
        }

        @Override
        public Bounds bounds() {
            return Bounds.EMPTY.include(p0).include(p1);
        }

        @Override
        public double[] yTurns() {
            return new double[0];
        }

        @Override
        public Edge reversed() {
            return new Line(p1, p0);
        }

        @Override
        public Edge[] thirds() {
            Vec2 a = point(1 / 3.0);
            Vec2 b = point(2 / 3.0);
            return new Edge[]{new Line(p0, a), new Line(a, b), new Line(b, p1)};
        }
    }

    /** A quadratic Bézier from {@code p0} to {@code p2}, pulled toward {@code p1}. */
    record Quadratic(Vec2 p0, Vec2 p1, Vec2 p2) implements Edge {

        @Override
        public Vec2 point(double t) {
            return Vec2.mix(Vec2.mix(p0, p1, t), Vec2.mix(p1, p2, t), t);
        }

        @Override
        public Vec2 direction(double t) {
            Vec2 d = Vec2.mix(p1.minus(p0), p2.minus(p1), t);
            return d.isZero() ? p2.minus(p0) : d;
        }

        @Override
        public Nearest nearest(Vec2 p) {
            Vec2 qa = p0.minus(p);
            Vec2 ab = p1.minus(p0);
            Vec2 br = p2.minus(p1).minus(ab);
            double[] roots = new double[3];
            int n = Equations.cubic(roots, br.dot(br), 3 * ab.dot(br), 2 * ab.dot(ab) + qa.dot(br), qa.dot(ab));

            Vec2 dir = direction(0);
            double min = sign(dir.cross(qa)) * qa.length();
            double param = -qa.dot(dir) / dir.dot(dir);
            Vec2 end = p2.minus(p);
            double endDistance = end.length();
            if (endDistance < Math.abs(min)) {
                dir = direction(1);
                min = sign(dir.cross(end)) * endDistance;
                param = p.minus(p1).dot(dir) / dir.dot(dir);
            }
            for (int i = 0; i < n; i++) {
                double t = roots[i];
                if (t > 0 && t < 1) {
                    Vec2 qe = qa.plus(ab.times(2 * t)).plus(br.times(t * t));
                    double d = qe.length();
                    if (d <= Math.abs(min)) {
                        min = sign(ab.plus(br.times(t)).cross(qe)) * d;
                        param = t;
                    }
                }
            }
            if (param >= 0 && param <= 1) {
                return new Nearest(new SignedDistance(min, 0), param);
            }
            Vec2 toEnd = param < 0.5 ? qa : end;
            Vec2 endDir = direction(param < 0.5 ? 0 : 1);
            return new Nearest(new SignedDistance(min, Math.abs(endDir.normalize().dot(toEnd.normalize()))), param);
        }

        @Override
        public Bounds bounds() {
            Bounds b = Bounds.EMPTY.include(p0).include(p2);
            Vec2 bot = p1.minus(p0).minus(p2.minus(p1));
            if (bot.x() != 0) {
                double t = (p1.x() - p0.x()) / bot.x();
                if (t > 0 && t < 1) {
                    b = b.include(point(t));
                }
            }
            if (bot.y() != 0) {
                double t = (p1.y() - p0.y()) / bot.y();
                if (t > 0 && t < 1) {
                    b = b.include(point(t));
                }
            }
            return b;
        }

        @Override
        public double[] yTurns() {
            double den = p0.y() - 2 * p1.y() + p2.y();
            if (den != 0) {
                double t = (p0.y() - p1.y()) / den;
                if (t > 0 && t < 1) {
                    return new double[]{t};
                }
            }
            return new double[0];
        }

        @Override
        public Edge reversed() {
            return new Quadratic(p2, p1, p0);
        }

        @Override
        public Edge[] thirds() {
            Vec2 a = point(1 / 3.0);
            Vec2 b = point(2 / 3.0);
            return new Edge[]{
                    new Quadratic(p0, Vec2.mix(p0, p1, 1 / 3.0), a),
                    new Quadratic(a, Vec2.mix(Vec2.mix(p0, p1, 5 / 9.0), Vec2.mix(p1, p2, 4 / 9.0), 0.5), b),
                    new Quadratic(b, Vec2.mix(p1, p2, 2 / 3.0), p2)};
        }
    }

    /** A cubic Bézier from {@code p0} to {@code p3}, by {@code p1} and {@code p2}. */
    record Cubic(Vec2 p0, Vec2 p1, Vec2 p2, Vec2 p3) implements Edge {

        /** How many starting points the nearest-point search tries, and how many Newton steps each takes. */
        private static final int STARTS = 8;
        private static final int STEPS = 6;

        @Override
        public Vec2 point(double t) {
            Vec2 m = Vec2.mix(p1, p2, t);
            return Vec2.mix(Vec2.mix(Vec2.mix(p0, p1, t), m, t), Vec2.mix(m, Vec2.mix(p2, p3, t), t), t);
        }

        @Override
        public Vec2 direction(double t) {
            Vec2 d = Vec2.mix(Vec2.mix(p1.minus(p0), p2.minus(p1), t), Vec2.mix(p2.minus(p1), p3.minus(p2), t), t);
            if (d.isZero()) {
                if (t == 0) {
                    return p2.minus(p0);
                }
                if (t == 1) {
                    return p3.minus(p1);
                }
            }
            return d;
        }

        @Override
        public Nearest nearest(Vec2 p) {
            Vec2 qa = p0.minus(p);
            Vec2 ab = p1.minus(p0);
            Vec2 br = p2.minus(p1).minus(ab);
            Vec2 as = p3.minus(p2).minus(p2.minus(p1)).minus(br);

            Vec2 dir = direction(0);
            double min = sign(dir.cross(qa)) * qa.length();
            double param = -qa.dot(dir) / dir.dot(dir);
            Vec2 end = p3.minus(p);
            double endDistance = end.length();
            if (endDistance < Math.abs(min)) {
                dir = direction(1);
                min = sign(dir.cross(end)) * endDistance;
                param = 1 + p.minus(p3).dot(dir) / dir.dot(dir);
            }
            for (int i = 0; i <= STARTS; i++) {
                double t = (double) i / STARTS;
                for (int step = 0; ; step++) {
                    Vec2 qe = qa.plus(ab.times(3 * t)).plus(br.times(3 * t * t)).plus(as.times(t * t * t));
                    Vec2 d1 = ab.times(3).plus(br.times(6 * t)).plus(as.times(3 * t * t));
                    double d = qe.length();
                    if (d < Math.abs(min) && t > 0 && t < 1) {
                        min = sign(d1.cross(qe)) * d;
                        param = t;
                    }
                    if (step == STEPS) {
                        break;
                    }
                    Vec2 d2 = br.times(6).plus(as.times(6 * t));
                    double den = d1.dot(d1) + qe.dot(d2);
                    if (den == 0) {
                        break;
                    }
                    t -= qe.dot(d1) / den;
                    if (t <= 0 || t >= 1) {
                        break;
                    }
                }
            }
            if (param >= 0 && param <= 1) {
                return new Nearest(new SignedDistance(min, 0), param);
            }
            Vec2 toEnd = param < 0.5 ? qa : end;
            Vec2 endDir = direction(param < 0.5 ? 0 : 1);
            return new Nearest(new SignedDistance(min, Math.abs(endDir.normalize().dot(toEnd.normalize()))), param);
        }

        @Override
        public Bounds bounds() {
            Bounds b = Bounds.EMPTY.include(p0).include(p3);
            for (double t : turns(true)) {
                b = b.include(point(t));
            }
            for (double t : turns(false)) {
                b = b.include(point(t));
            }
            return b;
        }

        @Override
        public double[] yTurns() {
            return turns(false);
        }

        /** Where the derivative's x (or y) is zero, strictly inside. */
        private double[] turns(boolean x) {
            double a0 = x ? p1.x() - p0.x() : p1.y() - p0.y();
            double a1 = x ? p2.x() - p1.x() : p2.y() - p1.y();
            double a2 = x ? p3.x() - p2.x() : p3.y() - p2.y();
            // B'(t)/3 = a0 (1-t)² + 2 a1 t (1-t) + a2 t²
            double[] roots = new double[2];
            int n = Equations.quadratic(roots, a0 - 2 * a1 + a2, 2 * (a1 - a0), a0);
            double[] out = new double[Math.max(n, 0)];
            int k = 0;
            for (int i = 0; i < n; i++) {
                if (roots[i] > 0 && roots[i] < 1) {
                    out[k++] = roots[i];
                }
            }
            double[] in = java.util.Arrays.copyOf(out, k);
            java.util.Arrays.sort(in);
            return in;
        }

        @Override
        public Edge reversed() {
            return new Cubic(p3, p2, p1, p0);
        }

        @Override
        public Edge[] thirds() {
            return new Edge[]{part(0, 1 / 3.0), part(1 / 3.0, 2 / 3.0), part(2 / 3.0, 1)};
        }

        /** The piece from {@code a} to {@code b}, by blossoming. */
        private Cubic part(double a, double b) {
            return new Cubic(point(a), blossom(a, a, b), blossom(a, b, b), point(b));
        }

        private Vec2 blossom(double u, double v, double w) {
            Vec2 q0 = Vec2.mix(p0, p1, u), q1 = Vec2.mix(p1, p2, u), q2 = Vec2.mix(p2, p3, u);
            Vec2 r0 = Vec2.mix(q0, q1, v), r1 = Vec2.mix(q1, q2, v);
            return Vec2.mix(r0, r1, w);
        }
    }
}
