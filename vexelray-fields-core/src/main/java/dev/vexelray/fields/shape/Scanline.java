package dev.vexelray.fields.shape;

import java.util.Arrays;

/**
 * Where a horizontal line crosses a shape, and which way each crossing goes: what says whether a point is filled,
 * by the nonzero rule, without trusting any distance's sign. A distance's sign is only as good as the contours'
 * winding; this holds for overlapping contours and ones wound either way.
 *
 * <p>Each edge is cut into pieces monotone in y, and a piece is crossed where the line's y is in {@code [low, high)}
 * of it: a vertex the outline passes through is counted once, and one it turns at is counted twice, opposite ways,
 * or not at all.
 */
public final class Scanline {

    private final double[] xs;
    private final int[] dirs;

    private Scanline(double[] xs, int[] dirs) {
        this.xs = xs;
        this.dirs = dirs;
    }

    /** The crossings of {@code shape} at height {@code y}. */
    public static Scanline of(Shape shape, double y) {
        double[] xs = new double[16];
        int[] dirs = new int[16];
        int n = 0;
        for (Contour c : shape.contours()) {
            for (Edge e : c.edges()) {
                double[] turns = e.yTurns();
                double ta = 0;
                for (int i = 0; i <= turns.length; i++) {
                    double tb = i < turns.length ? turns[i] : 1;
                    double ya = e.point(ta).y();
                    double yb = e.point(tb).y();
                    if (ya != yb && y >= Math.min(ya, yb) && y < Math.max(ya, yb)) {
                        if (n == xs.length) {
                            xs = Arrays.copyOf(xs, n * 2);
                            dirs = Arrays.copyOf(dirs, n * 2);
                        }
                        xs[n] = e.point(solve(e, ta, tb, ya, yb, y)).x();
                        dirs[n] = yb > ya ? 1 : -1;
                        n++;
                    }
                    ta = tb;
                }
            }
        }
        // sort by x, carrying the directions
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        double[] x0 = xs;
        Arrays.sort(order, (a, b) -> Double.compare(x0[a], x0[b]));
        double[] sx = new double[n];
        int[] sd = new int[n];
        for (int i = 0; i < n; i++) {
            sx[i] = xs[order[i]];
            sd[i] = dirs[order[i]];
        }
        return new Scanline(sx, sd);
    }

    /** The {@code t} in {@code [ta, tb]} where a y-monotone piece is at {@code y}, by bisection. */
    private static double solve(Edge e, double ta, double tb, double ya, double yb, double y) {
        if (e instanceof Edge.Line) {
            return ta + (tb - ta) * (y - ya) / (yb - ya);
        }
        boolean rising = yb > ya;
        double lo = ta, hi = tb;
        for (int i = 0; i < 52; i++) {
            double mid = 0.5 * (lo + hi);
            if ((e.point(mid).y() < y) == rising) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    /** The winding number at {@code x}: the sum of the directions of the crossings to its right. */
    public int winding(double x) {
        int w = 0;
        for (int i = xs.length - 1; i >= 0 && xs[i] > x; i--) {
            w += dirs[i];
        }
        return w;
    }

    /** Whether {@code x} is filled, by the nonzero rule. */
    public boolean filled(double x) {
        return winding(x) != 0;
    }

    /** How many crossings there are. */
    public int size() {
        return xs.length;
    }
}
