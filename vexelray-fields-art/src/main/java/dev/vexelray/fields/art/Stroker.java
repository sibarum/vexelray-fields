package dev.vexelray.fields.art;

import dev.vexelray.fields.shape.Edge;
import dev.vexelray.fields.shape.Path;
import dev.vexelray.fields.shape.Vec2;

import java.util.ArrayList;
import java.util.List;

/**
 * A stroke as the union of simple pieces, each covered on its own: a box along every straight piece, a join at every
 * vertex and a cap at every open end. Curves are cut into straight pieces finely enough, under a twentieth of a pixel
 * off, and joined round where they are cut, so no crack or notch shows where a curve bends. A path's own corners
 * take the join it asks for; miters past their limit are bevelled, as SVG has it.
 *
 * <p>Everything is in pixels: the path is already transformed, and the width with it, by the transform's mean
 * scale. A transform that stretches one way more than the other (a non-uniform scale, a skew) draws its strokes at
 * one width all round rather than as the ellipse a stretched pen would be.
 */
final class Stroker {

    enum Cap { BUTT, ROUND, SQUARE }

    enum Join { MITER, ROUND, BEVEL }

    /** The most a straight piece of a flattened curve may stray from the curve, in pixels. */
    private static final double FLATNESS = 0.05;

    private final Cover cover;
    private final double hw;
    private final Cap cap;
    private final Join join;
    private final double miterLimit;

    /** A polyline: its points, and which of them are the path's own corners rather than cuts in a curve. */
    private record Poly(List<Vec2> points, List<Boolean> corners) {

        Poly() {
            this(new ArrayList<>(), new ArrayList<>());
        }

        void add(Vec2 p, boolean corner) {
            if (!points.isEmpty() && points.getLast().equals(p)) {
                corners.set(corners.size() - 1, corners.getLast() || corner);
                return;
            }
            points.add(p);
            corners.add(corner);
        }
    }

    private Stroker(Cover cover, double width, Cap cap, Join join, double miterLimit) {
        this.cover = cover;
        this.hw = width / 2;
        this.cap = cap;
        this.join = join;
        this.miterLimit = miterLimit;
    }

    /**
     * Cover the stroke of {@code path}, in pixels, {@code width} wide; dashed by {@code dashes} from
     * {@code dashOffset} along, if they are not null.
     */
    static void stroke(Cover cover, Path path, double width, Cap cap, Join join, double miterLimit,
                       double[] dashes, double dashOffset) {
        if (!(width > 0)) {
            return;
        }
        Stroker s = new Stroker(cover, width, cap, join, miterLimit);
        for (Path.Subpath sub : path.subpaths()) {
            Poly poly = flatten(sub);
            if (dashes == null) {
                s.polyline(poly, sub.closed() && poly.points.size() > 2);
            } else {
                for (Poly dash : dash(poly, dashes, dashOffset)) {
                    s.polyline(dash, false);
                }
            }
        }
    }

    private static Poly flatten(Path.Subpath sub) {
        Poly p = new Poly();
        p.add(sub.start(), true);
        for (Edge e : sub.edges()) {
            int n = pieces(e);
            for (int j = 1; j <= n; j++) {
                p.add(e.point(j / (double) n), j == n);
            }
        }
        if (sub.closed()) {
            p.add(sub.start(), true);
        }
        return p;
    }

    /** How many straight pieces keep {@code e} within {@link #FLATNESS}: a curve strays at most its control points' reach. */
    private static int pieces(Edge e) {
        Vec2 a = e.start(), b = e.end();
        double reach = switch (e) {
            case Edge.Line l -> 0;
            case Edge.Quadratic q -> offChord(q.p1(), a, b);
            case Edge.Cubic c -> Math.max(offChord(c.p1(), a, b), offChord(c.p2(), a, b));
        };
        return (int) Math.clamp(Math.ceil(Math.sqrt(reach / FLATNESS)), 1, 1024);
    }

    private static double offChord(Vec2 p, Vec2 a, Vec2 b) {
        Vec2 ab = b.minus(a);
        double len = ab.length();
        return len == 0 ? p.minus(a).length() : Math.abs(ab.cross(p.minus(a))) / len;
    }

    /** {@code poly} cut into the dashes {@code pattern} lays along it, begun {@code offset} into the pattern. */
    private static List<Poly> dash(Poly poly, double[] pattern, double offset) {
        double total = 0;
        for (double d : pattern) {
            total += d;
        }
        List<Poly> out = new ArrayList<>();
        double phase = ((offset % total) + total) % total;
        int k = 0;
        while (phase >= pattern[k]) {
            phase -= pattern[k];
            k = (k + 1) % pattern.length;
        }
        double rem = pattern[k] - phase;
        boolean on = k % 2 == 0;
        List<Vec2> pts = poly.points;
        Poly current = null;
        if (on) {
            current = new Poly();
            current.add(pts.getFirst(), false);
        }
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vec2 a = pts.get(i), b = pts.get(i + 1);
            double len = b.minus(a).length();
            double pos = 0;
            while (len - pos > rem) {
                pos += rem;
                Vec2 q = Vec2.mix(a, b, pos / len);
                if (on) {
                    current.add(q, false);
                    out.add(current);
                    current = null;
                } else {
                    current = new Poly();
                    current.add(q, false);
                }
                on = !on;
                k = (k + 1) % pattern.length;
                rem = pattern[k];
            }
            rem -= len - pos;
            if (on) {
                current.add(b, poly.corners.get(i + 1));
            }
        }
        if (current != null) {
            out.add(current);
        }
        return out;
    }

    private void polyline(Poly poly, boolean closed) {
        List<Vec2> p = poly.points;
        int n = p.size();
        if (n == 1) {   // no length: a dot, if the cap has one
            Vec2 c = p.getFirst();
            if (cap == Cap.ROUND) {
                cover.disc(c, hw);
            } else if (cap == Cap.SQUARE) {
                cover.convex(c.plus(new Vec2(-hw, -hw)), c.plus(new Vec2(hw, -hw)), c.plus(new Vec2(hw, hw)),
                        c.plus(new Vec2(-hw, hw)));
            }
            return;
        }
        for (int i = 0; i + 1 < n; i++) {
            Vec2 a = p.get(i), b = p.get(i + 1);
            Vec2 nrm = normal(b.minus(a));
            cover.convex(a.plus(nrm), b.plus(nrm), b.minus(nrm), a.minus(nrm));
        }
        for (int i = 1; i + 1 < n; i++) {
            join(p.get(i), p.get(i).minus(p.get(i - 1)), p.get(i + 1).minus(p.get(i)), poly.corners.get(i));
        }
        if (closed) {   // the last point is the first again: join the last piece to the first
            join(p.getFirst(), p.getFirst().minus(p.get(n - 2)), p.get(1).minus(p.getFirst()), true);
        } else {
            cap(p.getFirst(), p.getFirst().minus(p.get(1)).normalize());
            cap(p.getLast(), p.getLast().minus(p.get(n - 2)).normalize());
        }
    }

    /** The half-width at right angles to {@code d}. */
    private Vec2 normal(Vec2 d) {
        Vec2 t = d.normalize();
        return new Vec2(-t.y() * hw, t.x() * hw);
    }

    /** A cap at end {@code p} of a polyline, {@code out} the unit direction leaving it. */
    private void cap(Vec2 p, Vec2 out) {
        switch (cap) {
            case ROUND -> cover.disc(p, hw);
            case SQUARE -> {
                Vec2 n = new Vec2(-out.y() * hw, out.x() * hw);
                Vec2 f = out.times(hw);
                cover.convex(p.plus(n), p.plus(n).plus(f), p.minus(n).plus(f), p.minus(n));
            }
            case BUTT -> {
            }
        }
    }

    private void join(Vec2 p, Vec2 in, Vec2 out, boolean corner) {
        if (!corner || join == Join.ROUND) {
            cover.disc(p, hw);
            return;
        }
        Vec2 n1 = normal(in), n2 = normal(out);
        Vec2 t1 = in.normalize(), t2 = out.normalize();
        double cos = t1.dot(t2);
        if (join == Join.MITER && 1 + cos > 1e-9) {
            double ratio = 1 / Math.sqrt((1 + cos) / 2);   // the miter's length over the stroke's width
            if (ratio <= miterLimit) {
                double s = t1.cross(t2) > 0 ? -1 : 1;   // the outer side of the turn
                Vec2 m = p.plus(n1.plus(n2).times(s / (1 + cos)));
                cover.convex(p, p.plus(n1.times(s)), m, p.plus(n2.times(s)));
                return;
            }
        }
        cover.convex(p, p.plus(n1), p.plus(n2));
        cover.convex(p, p.minus(n1), p.minus(n2));
    }
}
